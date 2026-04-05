{
  description = "Bleeding-Edge Dechainer build shell (Unstable)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }: 
    let
      system = "x86_64-linux";
      
      pkgs = import nixpkgs { 
        inherit system; 
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      androidEnv = pkgs.androidenv.composeAndroidPackages {
        cmdLineToolsVersion = "13.0";
        toolsVersion = "26.1.1";
        platformToolsVersion = "35.0.1"; 
        # CHANGED: Added 35.0.0 to match what Gradle is begging for
        buildToolsVersions = [ "35.0.0" "34.0.0" ]; 
        platformVersions = [ "36" "34" "30" ]; 
        includeEmulator = false;
        includeSystemImages = false;
        includeNDK = false;
      };

    in 
    {
      devShells.${system}.default = pkgs.mkShell {
        buildInputs = [
          pkgs.jdk17 
          pkgs.gradle
          androidEnv.androidsdk
        ];

        shellHook = ''
          export ANDROID_HOME=${androidEnv.androidsdk}/libexec/android-sdk
          export JAVA_HOME=${pkgs.jdk17}/lib/openjdk
          echo "==================================================="
          echo " Fixed: Added Build-Tools 35.0.0 to Nix Store"
          echo " ANDROID_HOME: $ANDROID_HOME"
          echo "==================================================="
        '';
      };
    };
}