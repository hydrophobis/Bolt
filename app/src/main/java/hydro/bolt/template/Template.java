// lowk ai generated
package hydro.bolt.template;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Template {
    private String template;

    public Template(String template) {
        this.template = template;
    }

    public String apply(Map<String, Object> values) {
        String result = template;

        // loops: {{repeat:NAME}} ... {{endrepeat}}
        Pattern repeatPattern = Pattern.compile("\\{\\{repeat:(\\w+)\\}\\}([\\s\\S]*?)\\{\\{endrepeat\\}\\}");
        Matcher matcher = repeatPattern.matcher(result);
        while (matcher.find()) {
            String key = matcher.group(1);
            String section = matcher.group(2);

            Object obj = values.get(key);
            if (obj instanceof List<?> list) {
                StringBuilder sb = new StringBuilder();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        sb.append(new Template(section).apply((Map<String, Object>) map));
                    } else {
                        sb.append(section.replace("{{item}}", item.toString()));
                    }
                }
                result = result.replace(matcher.group(0), sb.toString());
            } else {
                result = result.replace(matcher.group(0), "");
            }

            matcher = repeatPattern.matcher(result);
        }

        // placeholders: {{name}}
        Pattern placeholderPattern = Pattern.compile("\\{\\{(\\w+)\\}\\}");
        matcher = placeholderPattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = values.getOrDefault(key, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
