import {
  createConnection,
  TextDocuments,
  ProposedFeatures,
  InitializeParams,
  InitializeResult,
  TextDocumentSyncKind,
  CompletionItem,
  CompletionItemKind,
  Hover,
  MarkupKind,
  DiagnosticSeverity,
  Diagnostic,
  Location,
  Position,
  Range,
  TextDocumentPositionParams,
  DefinitionParams,
} from 'vscode-languageserver/node';
import { TextDocument } from 'vscode-languageserver-textdocument';

const connection = createConnection(ProposedFeatures.all);
const documents = new TextDocuments(TextDocument);

// ─── Types ───────────────────────────────────────────────────────────────────

type SymbolKind = 'function' | 'variable' | 'class' | 'struct' | 'parameter' | 'field';

interface BoltSymbol {
  name: string;
  kind: SymbolKind;
  type: string;
  line: number;
  col: number;
  uri: string;
  detail?: string;
}

// ─── Keyword / snippet data ───────────────────────────────────────────────────

const KEYWORDS = [
  'auto','break','case','char','const','continue','default','do','double',
  'else','enum','extern','float','for','goto','if','inline','int','long',
  'register','restrict','return','short','signed','sizeof','static','struct',
  'switch','typedef','union','unsigned','void','volatile','while',
  'class','public','private','protected','import','impl','operator','package',
  'new','delete','self','true','false','NULL',
];

const PRIMITIVE_TYPES = ['void','int','char','float','double','string','long','short','unsigned','signed','auto','bool'];

const SNIPPETS: CompletionItem[] = [
  {
    label: 'class',
    kind: CompletionItemKind.Snippet,
    insertText: 'class ${1:Name} {\n\t$0\n}',
    insertTextFormat: 2,
    detail: 'Class declaration',
  },
  {
    label: 'impl',
    kind: CompletionItemKind.Snippet,
    insertText: 'impl ${1:Type} {\n\t$0\n}',
    insertTextFormat: 2,
    detail: 'Impl block',
  },
  {
    label: 'fn',
    kind: CompletionItemKind.Snippet,
    insertText: '${1:void} ${2:name}(${3}) {\n\t$0\n}',
    insertTextFormat: 2,
    detail: 'Function declaration',
  },
  {
    label: 'if',
    kind: CompletionItemKind.Snippet,
    insertText: 'if (${1:condition}) {\n\t$0\n}',
    insertTextFormat: 2,
    detail: 'If statement',
  },
  {
    label: 'ifelse',
    kind: CompletionItemKind.Snippet,
    insertText: 'if (${1:condition}) {\n\t$2\n} else {\n\t$0\n}',
    insertTextFormat: 2,
    detail: 'If-else statement',
  },
  {
    label: 'for',
    kind: CompletionItemKind.Snippet,
    insertText: 'for (${1:int} ${2:i} = ${3:0}; ${2:i} < ${4:n}; ${2:i}++) {\n\t$0\n}',
    insertTextFormat: 2,
    detail: 'For loop',
  },
  {
    label: 'while',
    kind: CompletionItemKind.Snippet,
    insertText: 'while (${1:condition}) {\n\t$0\n}',
    insertTextFormat: 2,
    detail: 'While loop',
  },
  {
    label: 'operator',
    kind: CompletionItemKind.Snippet,
    insertText: 'operator ${1:ReturnType} ${2:left} ${3:op} ${4:right} {\n\t$0\n}',
    insertTextFormat: 2,
    detail: 'Binary operator overload',
  },
  {
    label: 'import',
    kind: CompletionItemKind.Snippet,
    insertText: 'import ${1:std.io}',
    insertTextFormat: 2,
    detail: 'Import statement',
  },
  {
    label: 'package',
    kind: CompletionItemKind.Snippet,
    insertText: 'package ${1:name}',
    insertTextFormat: 2,
    detail: 'Package declaration',
  },
];

// ─── Parser / analyser ────────────────────────────────────────────────────────

interface ParsedDocument {
  symbols: BoltSymbol[];
  diagnostics: Diagnostic[];
}

function parseDocument(doc: TextDocument): ParsedDocument {
  const text = doc.getText();
  const lines = text.split('\n');
  const symbols: BoltSymbol[] = [];
  const diagnostics: Diagnostic[] = [];

  // Track brace depth to detect unclosed blocks
  let braceDepth = 0;
  let parenDepth = 0;
  let inBlockComment = false;
  let inString = false;

  // Regexes for symbol extraction
  const classRe = /\b(class|struct)\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*<([^>]*)>)?/;
  const implRe = /\bimpl\s+([A-Za-z_][A-Za-z0-9_]*)/;
  const funcRe = /\b([A-Za-z_][A-Za-z0-9_*]*)\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*<[^>]*>)?\s*\(/;
  const varRe = /\b([A-Za-z_][A-Za-z0-9_*]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:=|;)/;
  const importRe = /\bimport\s+([A-Za-z_][A-Za-z0-9_.]*)/;

  for (let i = 0; i < lines.length; i++) {
    let line = lines[i];

    // Strip block comments
    if (inBlockComment) {
      const end = line.indexOf('*/');
      if (end !== -1) {
        inBlockComment = false;
        line = line.slice(end + 2);
      } else {
        continue;
      }
    }

    // Remove block comment starts
    let stripped = '';
    let j = 0;
    while (j < line.length) {
      if (!inString && line[j] === '/' && line[j + 1] === '*') {
        inBlockComment = true;
        const end = line.indexOf('*/', j + 2);
        if (end !== -1) {
          inBlockComment = false;
          j = end + 2;
        } else {
          break;
        }
        continue;
      }
      if (!inString && line[j] === '/' && line[j + 1] === '/') break; // line comment
      if (line[j] === '"' && (j === 0 || line[j - 1] !== '\\')) inString = !inString;
      if (!inString) stripped += line[j];
      j++;
    }
    if (inString) inString = false; // reset per line (simple heuristic)

    // Count braces/parens for balance checking
    for (const ch of stripped) {
      if (ch === '{') braceDepth++;
      else if (ch === '}') {
        braceDepth--;
        if (braceDepth < 0) {
          diagnostics.push({
            range: Range.create(i, stripped.indexOf('}'), i, stripped.indexOf('}') + 1),
            message: 'Unexpected closing brace',
            severity: DiagnosticSeverity.Error,
            source: 'bolt',
          });
          braceDepth = 0;
        }
      } else if (ch === '(') parenDepth++;
      else if (ch === ')') {
        parenDepth--;
        if (parenDepth < 0) parenDepth = 0;
      }
    }

    // Extract symbols
    const classMat = classRe.exec(stripped);
    if (classMat) {
      symbols.push({
        name: classMat[2],
        kind: classMat[1] === 'struct' ? 'struct' : 'class',
        type: classMat[1],
        line: i,
        col: stripped.indexOf(classMat[2]),
        uri: doc.uri,
        detail: classMat[3] ? `${classMat[1]} ${classMat[2]}<${classMat[3]}>` : `${classMat[1]} ${classMat[2]}`,
      });
    }

    const implMat = implRe.exec(stripped);
    if (implMat) {
      symbols.push({
        name: implMat[1],
        kind: 'class',
        type: 'impl',
        line: i,
        col: stripped.indexOf(implMat[1]),
        uri: doc.uri,
        detail: `impl ${implMat[1]}`,
      });
    }

    const funcMat = funcRe.exec(stripped);
    if (funcMat && !KEYWORDS.includes(funcMat[1]) && !KEYWORDS.includes(funcMat[2])) {
      symbols.push({
        name: funcMat[2],
        kind: 'function',
        type: funcMat[1],
        line: i,
        col: stripped.indexOf(funcMat[2]),
        uri: doc.uri,
        detail: `${funcMat[1]} ${funcMat[2]}(...)`,
      });
    }

    const varMat = varRe.exec(stripped);
    if (
      varMat &&
      !KEYWORDS.includes(varMat[1]) &&
      !KEYWORDS.includes(varMat[2]) &&
      !funcRe.test(stripped)
    ) {
      symbols.push({
        name: varMat[2],
        kind: 'variable',
        type: varMat[1],
        line: i,
        col: stripped.indexOf(varMat[2]),
        uri: doc.uri,
        detail: `${varMat[1]} ${varMat[2]}`,
      });
    }

    // Check for missing semicolons on simple statements (not after { } or keywords)
    const trimmed = stripped.trim();
    if (
      trimmed.length > 0 &&
      !trimmed.endsWith(';') &&
      !trimmed.endsWith('{') &&
      !trimmed.endsWith('}') &&
      !trimmed.endsWith(',') &&
      !trimmed.startsWith('//') &&
      !trimmed.startsWith('*') &&
      !trimmed.startsWith('@') &&
      !trimmed.startsWith('#') &&
      /^[a-zA-Z_]/.test(trimmed) &&
      !classRe.test(trimmed) &&
      !implRe.test(trimmed) &&
      !funcRe.test(trimmed) &&
      !/\b(if|else|while|for|do|switch|return|import|package|operator|class|struct|impl)\b/.test(trimmed) &&
      braceDepth > 0
    ) {
      diagnostics.push({
        range: Range.create(i, 0, i, line.length),
        message: 'Possible missing semicolon',
        severity: DiagnosticSeverity.Warning,
        source: 'bolt',
      });
    }
  }

  // Unclosed brace at EOF
  if (braceDepth > 0) {
    diagnostics.push({
      range: Range.create(lines.length - 1, 0, lines.length - 1, 0),
      message: `Unclosed block: ${braceDepth} brace(s) not closed`,
      severity: DiagnosticSeverity.Error,
      source: 'bolt',
    });
  }

  return { symbols, diagnostics };
}

// ─── Per-document symbol cache ────────────────────────────────────────────────

const symbolCache = new Map<string, BoltSymbol[]>();
const importCache = new Map<string, string[]>(); // uri -> list of imported file uris

function getWordAtPosition(doc: TextDocument, pos: Position): string {
  const line = doc.getText(Range.create(pos.line, 0, pos.line, 9999));
  let start = pos.character;
  let end = pos.character;
  while (start > 0 && /\w/.test(line[start - 1])) start--;
  while (end < line.length && /\w/.test(line[end])) end++;
  return line.slice(start, end);
}

// Build a global symbol table from all open documents
function getGlobalSymbols(): BoltSymbol[] {
  const allSymbols: BoltSymbol[] = [];
  for (const [uri, symbols] of symbolCache) {
    allSymbols.push(...symbols);
  }
  return allSymbols;
}

// ─── LSP lifecycle ────────────────────────────────────────────────────────────

connection.onInitialize((_params: InitializeParams): InitializeResult => {
  return {
    capabilities: {
      textDocumentSync: TextDocumentSyncKind.Incremental,
      completionProvider: { resolveProvider: false, triggerCharacters: ['.', '@'] },
      hoverProvider: true,
      definitionProvider: true,
    },
  };
});

documents.onDidChangeContent((change) => {
  const parsed = parseDocument(change.document);
  symbolCache.set(change.document.uri, parsed.symbols);

  // Extract imports for cross-file resolution
  const imports: string[] = [];
  const text = change.document.getText();
  const importMatches = text.matchAll(/\bimport\s+([A-Za-z_][A-Za-z0-9_.]*)/g);
  for (const match of importMatches) {
    imports.push(match[1]);
  }
  importCache.set(change.document.uri, imports);

  connection.sendDiagnostics({ uri: change.document.uri, diagnostics: parsed.diagnostics });
});

documents.onDidClose((e) => {
  symbolCache.delete(e.document.uri);
  connection.sendDiagnostics({ uri: e.document.uri, diagnostics: [] });
});

// ─── Completion ───────────────────────────────────────────────────────────────

connection.onCompletion((params: TextDocumentPositionParams): CompletionItem[] => {
  const doc = documents.get(params.textDocument.uri);
  if (!doc) return [];

  const line = doc.getText(Range.create(params.position.line, 0, params.position.line, params.position.character));
  const isMemberAccess = /\.\s*\w*$/.test(line);

  const items: CompletionItem[] = [...SNIPPETS];

  // Keywords
  for (const kw of KEYWORDS) {
    items.push({ label: kw, kind: CompletionItemKind.Keyword });
  }

  // Primitive types
  for (const t of PRIMITIVE_TYPES) {
    items.push({ label: t, kind: CompletionItemKind.TypeParameter });
  }

  // Symbols from current document
  const symbols = symbolCache.get(params.textDocument.uri) ?? [];
  for (const sym of symbols) {
    const kind =
      sym.kind === 'function' ? CompletionItemKind.Function :
      sym.kind === 'class' || sym.kind === 'struct' ? CompletionItemKind.Class :
      CompletionItemKind.Variable;

    items.push({
      label: sym.name,
      kind,
      detail: sym.detail,
    });
  }

  // If member access, try to infer the type and suggest appropriate members
  if (isMemberAccess) {
    // Get the text before the dot to infer the type
    const beforeDot = line.substring(0, line.lastIndexOf('.')).trim();

    // Check if it's 'self' - suggest class members
    if (beforeDot === 'self') {
      const classMembers = symbols.filter(s => s.kind === 'function' || s.kind === 'field');
      for (const m of classMembers) {
        items.push({
          label: m.name,
          kind: m.kind === 'function' ? CompletionItemKind.Method : CompletionItemKind.Field,
          detail: m.detail,
        });
      }
    } else {
      // Try to find the variable declaration to get its type
      const varSymbol = symbols.find(s => s.name === beforeDot);
      if (varSymbol) {
        // If it's a class/struct type, look for impl blocks
        const implBlocks = symbols.filter(s => s.kind === 'class' && s.type === 'impl' && s.name === varSymbol.type);
        for (const impl of implBlocks) {
          // Find methods/fields in this impl block
          const implMembers = symbols.filter(s =>
            s.kind === 'function' &&
            s.line > impl.line &&
            (s.line < symbols.find((nextSym, idx) =>
              nextSym.kind === 'class' && nextSym.type === 'impl' && nextSym.line > impl.line && idx > symbols.indexOf(impl)
            )?.line || Infinity)
          );
          for (const m of implMembers) {
            items.push({
              label: m.name,
              kind: CompletionItemKind.Method,
              detail: m.detail,
            });
          }
        }
      }
    }
  }

  return items;
});

// ─── Hover ────────────────────────────────────────────────────────────────────

const HOVER_DOCS: Record<string, string> = {
  class: 'Declares a class type with optional generic parameters.\n\n```bolt\nclass Name<T> { ... }\n```',
  struct: 'Declares a struct (value type).\n\n```bolt\nstruct Point { int x; int y; }\n```',
  impl: 'Adds methods to an existing type, including primitives.\n\n```bolt\nimpl int { int square() { return self * self; } }\n```',
  operator: 'Overloads a binary or unary operator.\n\n```bolt\noperator int ] int { if (a > b) return a; return b; }\n```',
  import: 'Imports a module.\n\n```bolt\nimport std.io\n```',
  package: 'Declares the package for this file.\n\n```bolt\npackage my.pkg\n```',
  self: 'Reference to the current instance inside a class or impl method.',
  new: 'Allocates a new instance on the heap.\n\n```bolt\nPlayer* p = new Player();\n```',
  delete: 'Frees heap-allocated memory.\n\n```bolt\ndelete p;\n```',
  sizeof: 'Returns the size in bytes of a type or expression.',
  void: 'Represents the absence of a value.',
  int: 'Signed integer type.',
  float: 'Single-precision floating-point type.',
  double: 'Double-precision floating-point type.',
  char: 'Single character type.',
  string: 'String type (maps to `char*` in C output).',
  bool: 'Boolean type.',
  true: 'Boolean true constant.',
  false: 'Boolean false constant.',
  NULL: 'Null pointer constant.',
};

connection.onHover((params: TextDocumentPositionParams): Hover | null => {
  const doc = documents.get(params.textDocument.uri);
  if (!doc) return null;

  const word = getWordAtPosition(doc, params.position);
  if (!word) return null;

  // Check built-in docs
  if (HOVER_DOCS[word]) {
    return {
      contents: { kind: MarkupKind.Markdown, value: HOVER_DOCS[word] },
    };
  }

  // Check document symbols
  const symbols = symbolCache.get(params.textDocument.uri) ?? [];
  const sym = symbols.find(s => s.name === word);
  if (sym) {
    const kindLabel = sym.kind.charAt(0).toUpperCase() + sym.kind.slice(1);
    return {
      contents: {
        kind: MarkupKind.Markdown,
        value: `**${kindLabel}** \`${sym.detail ?? sym.name}\``,
      },
    };
  }

  // Check global symbols
  const globalSymbols = getGlobalSymbols();
  const globalSym = globalSymbols.find(s => s.name === word);
  if (globalSym) {
    const kindLabel = globalSym.kind.charAt(0).toUpperCase() + globalSym.kind.slice(1);
    return {
      contents: {
        kind: MarkupKind.Markdown,
        value: `**${kindLabel}** \`${globalSym.detail ?? globalSym.name}\``,
      },
    };
  }

  return null;
});

// ─── Go-to-definition ─────────────────────────────────────────────────────────

connection.onDefinition((params: DefinitionParams): Location | null => {
  const doc = documents.get(params.textDocument.uri);
  if (!doc) return null;

  const word = getWordAtPosition(doc, params.position);
  if (!word) return null;

  // First check current document
  const symbols = symbolCache.get(params.textDocument.uri) ?? [];
  const sym = symbols.find(s => s.name === word);
  if (sym) {
    return Location.create(
      sym.uri,
      Range.create(sym.line, sym.col, sym.line, sym.col + sym.name.length)
    );
  }

  // If not found in current document, search all open documents
  const globalSymbols = getGlobalSymbols();
  const globalSym = globalSymbols.find(s => s.name === word);
  if (globalSym) {
    return Location.create(
      globalSym.uri,
      Range.create(globalSym.line, globalSym.col, globalSym.line, globalSym.col + globalSym.name.length)
    );
  }

  return null;
});

// ─── Boot ─────────────────────────────────────────────────────────────────────

documents.listen(connection);
connection.listen();
