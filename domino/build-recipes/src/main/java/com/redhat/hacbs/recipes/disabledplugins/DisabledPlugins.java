package com.redhat.hacbs.recipes.disabledplugins;

import java.util.List;

public class DisabledPlugins {
    private List<String> disabledPlugins;

    public DisabledPlugins() {

    }

    public DisabledPlugins(String... disabledPlugins) {
        this.disabledPlugins = List.of(disabledPlugins);
    }

    public List<String> getDisabledPlugins() {
        return disabledPlugins;
    }

    public DisabledPlugins setDisabledPlugins(List<String> disabledPlugins) {
        this.disabledPlugins = disabledPlugins;
        return this;
    }
}
