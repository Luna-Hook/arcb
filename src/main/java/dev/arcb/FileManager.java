package dev.arcb;

import java.util.List;

interface FileManager {

    void loadAll();
    void stopAll();

    int enableFile(String name);
    int disableFile(String name);
    int enableGroup(String name);
    int disableGroup(String name);

    boolean isEnabled(String name);
    boolean isGroupEnabled(String name);

    List<String> getAllNames();
    List<String> getEnabledNames();
    List<String> getDisabledNames();
    List<String> getAllGroups();

    int getActiveCount();

    int addWhitelist(String name, String player);
    int removeWhitelist(String name, String player);
    List<String> getWhitelist(String name);
}
