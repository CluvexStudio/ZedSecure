{
  description = "ZedSecure — VPN client";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachSystem [ "x86_64-linux" ] (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        version = "3.1.2";

        zedsecure = pkgs.stdenv.mkDerivation {
          pname = "zedsecure";
          inherit version;

          src = pkgs.fetchurl {
            url = "https://github.com/CluvexStudio/ZedSecure/releases/download/desktop-v${version}/ZedSecure-${version}-linux-x86_64.tar.gz";
            hash = "sha256-tWjEs9JKIbjqYrZJuTpfJNWS56M6q5jykPwaw9i7XNQ=";
          };

          nativeBuildInputs = with pkgs; [ autoPatchelfHook makeWrapper copyDesktopItems ];

          buildInputs = with pkgs; [
            alsa-lib at-spi2-atk cairo cups.lib dbus.lib expat fontconfig
            freetype gdk-pixbuf glib gtk3 libGL libxkbcommon nss nspr pango
            stdenv.cc.cc.lib xorg.libX11 xorg.libXext xorg.libXi xorg.libXrender
            xorg.libXtst zlib
          ];

          desktopItems = [
            (pkgs.makeDesktopItem {
              name = "zedsecure";
              exec = "zedsecure";
              icon = "zedsecure";
              desktopName = "ZedSecure";
              categories = [ "Network" ];
            })
          ];

          installPhase = ''
            runHook preInstall
            mkdir -p $out/share/zedsecure $out/bin
            cp -r ./* $out/share/zedsecure/

            bin=$(find $out/share/zedsecure/bin -maxdepth 1 -type f -perm -u+x | head -1)
            makeWrapper "$bin" $out/bin/zedsecure \
              --prefix LD_LIBRARY_PATH : ${pkgs.lib.makeLibraryPath [ pkgs.systemdLibs ]}

            icon=$(find $out/share/zedsecure -name '*.png' | head -1)
            if [ -n "$icon" ]; then
              install -Dm444 "$icon" $out/share/icons/hicolor/512x512/apps/zedsecure.png
            fi
            runHook postInstall
          '';

          meta = with pkgs.lib; {
            description = "VPN client with Xray, sing-box, Psiphon, Tor and DNS tunnels";
            homepage = "https://github.com/CluvexStudio/ZedSecure";
            license = licenses.agpl3Plus;
            platforms = [ "x86_64-linux" ];
            mainProgram = "zedsecure";
          };
        };
      in
      {
        packages.default = zedsecure;
        packages.zedsecure = zedsecure;

        apps.default = flake-utils.lib.mkApp { drv = zedsecure; };
      });
}
