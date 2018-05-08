# EmpaLeague by Sam Snyder

## Introduction

This project makes use of the Empatica E4 wristband.

- This project makes use of the sample project by Empatica posted here:
 https://github.com/empatica/empalink-sample-project-android
- It initializes the EmpaLink library with your API key pertaining to your wristband.
- If the previous step is successful, it starts scanning for Empatica devices, till it finds one that can be used with 
    the API key you inserted in the code.
- When such a device has been found, the app connects to the devices and streams data for 25 minutes, then it disconnects.

## ON RUN
- The application searches for the E4 device.
- After connecting it begins running the application and streaming the data to the android device.
- Everytime that the "didReceiveIBI" function is called the program calculates the Heart Rate(HR).
- Following the calculatin of the HR, the HR values are added to an array list and a string array(hrCSV).
- The array list is used to calculate the simple moving average of the user's HR.
- The string array is used to write the HR values as well as timestamps and song start and stop markers to the txt file.
- After the list is filled once and only when the list is filled for the first time is the threshold calculated.
- If on recieving a new IBI values from the E4 device the SMA>Threshold, the application starts a new activity.
- This activity opens spotify and begins playing the song Canon in D Major.
- After the songs finishes, the spotify activity is destroyed and the app continues to run until the 25 minutes are up.
- On Disconnect the application writes the stored Heart Rate values to the txt file. (NOTE: The application ONLY writes the 
    data to the txt file on the APPLICATION'S disconnect of the E4 device.)

## Setup

1. Clone / download this repository.
2. Open the project in Android Studio.
3. Make sure you have a valid API key. 
4. Edit `MainActivity.java` and assign your API key to the `EMPATICA_API_KEY` constant .
5. Make sure Spotify is installed on the Android device and that a user is signed into the application.
6. Run the project.

## Misc.
- The txt file containing the heart rate values and timestamps is located on the sdcard under the folder 
called "test", the file is called "savedFile.txt". 
- When the threshold is calculated it will be printed to the console on Android Studio.
- When an increase in HR is detected the following message will be printed to console: 
"Increase in heart Rate confirmed: Music START"
