package hmgapi.hook;

import java.util.HashMap;
import java.util.Map;

public final class ModContext {
    public final String id = "projection-tbt";
    public final Map<String, Boolean> settings = new HashMap<String, Boolean>();
    public boolean settingBoolean(String key, boolean fallback) {
        return settings.containsKey(key) ? settings.get(key) : fallback;
    }
}
