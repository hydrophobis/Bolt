#!/usr/bin/env python3
# Generated with Claude
"""Differential fuzzer for Bolt's expression code generation.

Generates random C-compatible expressions, emits them as both a Bolt program
and an equivalent C program, compiles both, and compares the output. clang is
the oracle: any difference is a Bolt miscompile.

Expressions are rejected before emission if evaluating them would be undefined
behaviour in C (divide by zero, signed overflow, out-of-range shift), since
those are cases where the two compilers may legitimately disagree.

    python tools/fuzz_expressions.py --count 5000
    python tools/fuzz_expressions.py --seed 12345        # reproduce a failure
"""

import argparse
import os
import random
import shutil
import subprocess
import sys
import tempfile

INT_MIN = -(2 ** 31)
INT_MAX = 2 ** 31 - 1

VARS = {"a": 13, "b": 3, "c": 5, "d": 2, "e": 7, "f": 11}

ARITH = ["+", "-", "*", "/", "%"]
BITWISE = ["&", "|", "^"]
SHIFT = ["<<", ">>"]
COMPARE = ["<", "<=", ">", ">=", "==", "!="]
LOGICAL = ["&&", "||"]
BINOPS = ARITH + BITWISE + SHIFT + COMPARE + LOGICAL

PRECEDENCE = {
    "||": 1, "&&": 2, "|": 3, "^": 4, "&": 5,
    "==": 6, "!=": 6,
    "<": 7, "<=": 7, ">": 7, ">=": 7,
    "<<": 8, ">>": 8,
    "+": 9, "-": 9,
    "*": 10, "/": 10, "%": 10,
}
TERNARY_PREC = 0
UNARY_PREC = 11
ATOM_PREC = 12


class UndefinedBehaviour(Exception):
    pass


def wrap32(value):
    return ((value + 2 ** 31) % 2 ** 32) - 2 ** 31


def check_range(value):
    if value < INT_MIN or value > INT_MAX:
        raise UndefinedBehaviour("signed overflow")
    return value


def c_div(left, right):
    if right == 0:
        raise UndefinedBehaviour("divide by zero")
    if left == INT_MIN and right == -1:
        raise UndefinedBehaviour("INT_MIN / -1")
    quotient = abs(left) // abs(right)
    return -quotient if (left < 0) != (right < 0) else quotient


def c_mod(left, right):
    if right == 0:
        raise UndefinedBehaviour("modulo by zero")
    if left == INT_MIN and right == -1:
        raise UndefinedBehaviour("INT_MIN % -1")
    return left - c_div(left, right) * right


def evaluate(node):
    kind = node[0]
    if kind == "lit":
        return node[1]
    if kind == "var":
        return VARS[node[1]]

    if kind == "un":
        _, op, operand = node
        value = evaluate(operand)
        if op == "-":
            return check_range(-value)
        if op == "!":
            return 0 if value else 1
        if op == "~":
            return wrap32(~value)

    if kind == "ter":
        _, cond, when_true, when_false = node
        return evaluate(when_true) if evaluate(cond) else evaluate(when_false)

    _, op, left_node, right_node = node

    if op in LOGICAL:
        left = evaluate(left_node)
        if op == "&&":
            return 1 if (left and evaluate(right_node)) else 0
        return 1 if (left or evaluate(right_node)) else 0

    left = evaluate(left_node)
    right = evaluate(right_node)

    if op == "+":
        return check_range(left + right)
    if op == "-":
        return check_range(left - right)
    if op == "*":
        return check_range(left * right)
    if op == "/":
        return c_div(left, right)
    if op == "%":
        return c_mod(left, right)
    if op in COMPARE:
        return 1 if {
            "<": left < right, "<=": left <= right,
            ">": left > right, ">=": left >= right,
            "==": left == right, "!=": left != right,
        }[op] else 0
    if op == "&":
        return wrap32(left & right)
    if op == "|":
        return wrap32(left | right)
    if op == "^":
        return wrap32(left ^ right)
    if op in SHIFT:
        if right < 0 or right > 31:
            raise UndefinedBehaviour("shift out of range")
        if left < 0:
            raise UndefinedBehaviour("shift of negative value")
        if op == "<<":
            return check_range(left << right)
        return left >> right

    raise AssertionError("unhandled operator " + op)


def generate(rng, depth):
    if depth <= 0:
        if rng.random() < 0.5:
            return ("var", rng.choice(list(VARS)))
        return ("lit", rng.randint(0, 15))

    roll = rng.random()
    if roll < 0.12:
        return ("un", rng.choice(["-", "!", "~"]), generate(rng, depth - 1))
    if roll < 0.22:
        return ("ter", generate(rng, depth - 1),
                generate(rng, depth - 1), generate(rng, depth - 1))

    op = rng.choice(BINOPS)
    if op in SHIFT:
        return (op, ) and ("bin", op, generate(rng, depth - 1), ("lit", rng.randint(0, 15)))
    return ("bin", op, generate(rng, depth - 1), generate(rng, depth - 1))


def precedence_of(node):
    kind = node[0]
    if kind in ("lit", "var"):
        return ATOM_PREC
    if kind == "un":
        return UNARY_PREC
    if kind == "ter":
        return TERNARY_PREC
    return PRECEDENCE[node[1]]


def render(node, rng, parent_prec=0, is_right_child=False):
    kind = node[0]

    if kind == "lit":
        text = str(node[1])
    elif kind == "var":
        text = node[1]
    elif kind == "un":
        operand = render(node[2], rng, UNARY_PREC)
        if operand[:1] in ("-", "+", "~", "!"):
            operand = "(" + operand + ")"
        text = node[1] + operand
    elif kind == "ter":
        text = "%s ? %s : %s" % (
            render(node[1], rng, TERNARY_PREC + 1),
            render(node[2], rng, 0),
            render(node[3], rng, TERNARY_PREC),
        )
    else:
        op = node[1]
        prec = PRECEDENCE[op]
        text = "%s %s %s" % (
            render(node[2], rng, prec),
            op,
            render(node[3], rng, prec, is_right_child=True),
        )

    prec = precedence_of(node)
    needs = prec < parent_prec or (prec == parent_prec and is_right_child)
    # Redundant parentheses are always safe and exercise grouping preservation.
    if needs or rng.random() < 0.25:
        return "(" + text + ")"
    return text


def trips_generic_ambiguity(text):
    """Bolt reads `x < y > (z)` as a generic instantiation, so skip that shape.

    Tracked as a known parser bug; without this filter every batch trips over
    it and the rest of the expression space never gets explored.
    """
    stripped = text.replace("<<", "").replace(">>", "")
    stripped = stripped.replace("<=", "").replace(">=", "")
    open_at = stripped.find("<")
    return open_at != -1 and stripped.find(">", open_at) != -1


def build_case(rng, depth):
    for _ in range(200):
        tree = generate(rng, depth)
        try:
            evaluate(tree)
        except UndefinedBehaviour:
            continue
        except RecursionError:
            continue
        text = render(tree, rng)
        if trips_generic_ambiguity(text):
            continue
        return tree, text
    return None, None


def emit(path_bolt, path_c, exprs):
    decls = " ".join("int %s = %d;" % (name, value) for name, value in VARS.items())
    body = ["    " + decls, ""]
    for expr in exprs:
        body.append('    printf("%%d\\n", (int)(%s));' % expr)

    common = "\n".join(["int main() {"] + body + ["", "    return 0;", "}"])
    with open(path_bolt, "w", newline="\n") as handle:
        handle.write("import stdio;\n\n" + common + "\n")
    with open(path_c, "w", newline="\n") as handle:
        handle.write("#include <stdio.h>\n\n" + common + "\n")


def run(cmd, cwd=None):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=cwd)


class Harness:
    def __init__(self, classpath, workdir, cc):
        self.classpath = classpath
        self.workdir = workdir
        self.cc = cc

    def outputs_for(self, exprs):
        bolt_src = os.path.join(self.workdir, "fuzz.bolt")
        bolt_out = os.path.join(self.workdir, "fuzz.c")
        ref_src = os.path.join(self.workdir, "fuzz_ref.c")
        bolt_exe = os.path.join(self.workdir, "fuzz_bolt.exe")
        ref_exe = os.path.join(self.workdir, "fuzz_ref.exe")

        emit(bolt_src, ref_src, exprs)

        result = run('java -cp "%s" hydro.bolt.Bolt "%s" "%s"'
                     % (self.classpath, bolt_src, bolt_out), cwd=self.workdir)
        if "error[" in result.stdout + result.stderr:
            return None, None, "bolt rejected the program:\n" + result.stdout + result.stderr

        compiled = run('%s -w "%s" -o "%s"' % (self.cc, bolt_out, bolt_exe))
        if compiled.returncode != 0:
            return None, None, "generated C did not compile:\n" + compiled.stderr[:4000]

        reference = run('%s -w "%s" -o "%s"' % (self.cc, ref_src, ref_exe))
        if reference.returncode != 0:
            return None, None, "reference C did not compile:\n" + reference.stderr[:2000]

        got = run('"%s"' % bolt_exe).stdout.strip().splitlines()
        want = run('"%s"' % ref_exe).stdout.strip().splitlines()
        return got, want, None

    def mismatches(self, expr):
        got, want, problem = self.outputs_for([expr])
        if problem is not None:
            return True
        return got != want


def shrink(harness, expr, tree, rng):
    """Replace subtrees with their constant value while the mismatch survives."""
    best = expr

    def attempt(node, path):
        if not path:
            return ("lit", evaluate(node))
        index = path[0]
        return node[:index] + (attempt(node[index], path[1:]),) + node[index + 1:]

    def paths(node, prefix=()):
        kind = node[0]
        if kind in ("lit", "var"):
            return []
        children = range(2, len(node)) if kind == "bin" else range(1, len(node))
        if kind == "ter":
            children = range(1, 4)
        elif kind == "un":
            children = range(2, 3)
        found = []
        for index in children:
            found.append(prefix + (index,))
            found.extend(paths(node[index], prefix + (index,)))
        return found

    improved = True
    current_tree = tree
    while improved:
        improved = False
        for path in paths(current_tree):
            try:
                candidate_tree = attempt(current_tree, path)
                candidate = render(candidate_tree, rng)
            except (UndefinedBehaviour, IndexError, KeyError):
                continue
            if len(candidate) >= len(best):
                continue
            if harness.mismatches(candidate):
                best = candidate
                current_tree = candidate_tree
                improved = True
                break
    return best


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--count", type=int, default=2000)
    parser.add_argument("--seed", type=int, default=None)
    parser.add_argument("--depth", type=int, default=4)
    parser.add_argument("--batch", type=int, default=250)
    parser.add_argument("--classpath", default="app/build/classes/java/main")
    parser.add_argument("--cc", default="clang")
    parser.add_argument("--keep-going", action="store_true")
    args = parser.parse_args()

    if shutil.which(args.cc) is None:
        print("error: %s not found on PATH" % args.cc)
        return 2
    if not os.path.isdir(args.classpath):
        print("error: classpath %s not found; run ./gradlew :app:compileJava first"
              % args.classpath)
        return 2

    seed = args.seed if args.seed is not None else random.randrange(2 ** 32)
    rng = random.Random(seed)
    print("seed %d, %d expressions, depth %d" % (seed, args.count, args.depth))

    workdir = tempfile.mkdtemp(prefix="bolt-fuzz-")
    harness = Harness(os.path.abspath(args.classpath), workdir, args.cc)
    failures = 0
    checked = 0

    try:
        while checked < args.count:
            size = min(args.batch, args.count - checked)
            trees, exprs = [], []
            while len(exprs) < size:
                tree, text = build_case(rng, rng.randint(1, args.depth))
                if text is None:
                    continue
                trees.append(tree)
                exprs.append(text)

            got, want, problem = harness.outputs_for(exprs)
            checked += len(exprs)

            if problem is not None:
                print("\nBATCH FAILED: %s" % problem)
                failures += 1
                if not args.keep_going:
                    break
                continue

            for index, expr in enumerate(exprs):
                mine = got[index] if index < len(got) else "<missing>"
                theirs = want[index] if index < len(want) else "<missing>"
                if mine == theirs:
                    continue
                failures += 1
                print("\nMISMATCH  bolt=%s  clang=%s" % (mine, theirs))
                print("  %s" % expr)
                reduced = shrink(harness, expr, trees[index], rng)
                if reduced != expr:
                    print("  shrunk to: %s" % reduced)
                if not args.keep_going:
                    break

            if failures and not args.keep_going:
                break

            print("  %d/%d checked" % (checked, args.count), end="\r", flush=True)
    finally:
        if failures:
            print("\nartifacts kept in %s" % workdir)
        else:
            shutil.rmtree(workdir, ignore_errors=True)

    print(" " * 40, end="\r")
    if failures:
        print("\n%d mismatch(es) found. Reproduce with --seed %d" % (failures, seed))
        return 1
    print("\n%d expressions checked, no mismatches (seed %d)" % (checked, seed))
    return 0


if __name__ == "__main__":
    sys.exit(main())
