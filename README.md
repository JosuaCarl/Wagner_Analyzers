# Wagner Analyzers (image, Z-Stack and video analysis with Fiji workflow)

A script that navigates through folders and subfolders of a starting point, miming the folder structure at a given output path, and opening all files with a defined suffix for further analysis. The analysis separates java input into separate channels, merges and saves them with a naming scheme that can be adjusted in the code. The script detects automatically, whether the given file is a image, z-stack or video and will adjust analysis accordingly. Just try it out on your java images and see the magic happen.

## Via editor
### Navigation
The Fiji executable macros are found in [macros](macros/). They incorporate all complete versions of the image analysis macros in different iterations.
- Analyzer-v1.ijm
- Analyzer-v2.java

In addition, [src/main/ijAnalysis.java](src/main/ijAnalysis.microscopy) contains the Analyzer as a testable java project, installable via Gradle (`gradlew install`).

### Installation
Just download the `.ijm` / `.java` files or clone the repository with a method to your liking.

### Application
I prefer opening Fiji and then navigating to `Plugins -> Macros -> Edit`. Then click on `File -> Open` and select the file. After that just click `Run` or select `Run -> Run` in the upper menu.

## Via plugins directory
Copy the `Wagner_Analyzers-<version>.jar` file from a release into your `Fiji/plugins` folder. Upon restart Fiji will display the menu options "Wagner Analyzers".
