# Bloodborne Compact Startup

Source overlay for the tested Fabric 1.20.1 JAR. The original Seam Repair source tree is inherited from v22-bloodborne-seam-repair.1.

The state-startup-step2 directory contains the state transition optimization.
The core and selectors directories contain the new client mixins.

The scripts record the original Windows build environment. Adapt their paths to your local setup with Java 17, Yarn 1.20.1 build 10, the Fabric libraries, and the original Seam Repair JAR. Stage state-startup-step2 beside the directory containing the top-level package.py. Build and package Step 2 first, compile core and selectors, then run the top-level package.py. The input archives are pinned by SHA-256.
