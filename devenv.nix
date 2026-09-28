{ pkgs, ... }:

{
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
    gradle.enable = true;
    lsp.enable = false;
  };

  android = {
    enable = true;
    platforms.version = [ "35" ];
    buildTools.version = [ "35.0.0" ];
    emulator.enable = false;
    systemImages.enable = false;
    sources.enable = false;
    ndk.enable = false;
    googleAPIs.enable = false;
    googleTVAddOns.enable = false;
    extras = [ ];
  };

  packages = [ pkgs.android-tools ];

  scripts = {
    build-app.exec = "./gradlew assembleDebug";
    test-app.exec = "./gradlew test";
  };
}
