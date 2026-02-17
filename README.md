# InternalStorageOverkill

## Overview
Intrakill is a sqlite encrypted media storage. It's obviously overkill and I named it after that.  
Still, if you always wanted personal media storage that primarily aims you to conveniently navigate
through grouped media attachments, you might have found it. I find it shines best with manga and comics.  
Storage consists of list of entries. Each entry consists of name, attachments and tags.  

## Technical

It's a kotlin multiplatform application and supports jvm desktop and android. I have no reason to mess with iOS for now.  
Implementation is heavily influenced with claude, chatgpt and gemini. KMP by 02.2026 has very rough learning curve compared to 
my previous experience with flutter and even reactjs. I had to interrogate AI on many design solutions, and some of them still didn't feel right.  
Until I bumped into [Decompose](https://github.com/arkivanov/Decompose).  
As for someone who has mostly backend experience, this made my experience go from  
"oh my god, I hope this new stateful value in my component won't trigger repetitive rerenders"  
to  
"god, this is so straightforward and natural".  
Now, there still were some edgecases, but I can't stress enough how good that library is.

### Import/Export

So far, it's designed to allow importing entire storage from desktop version within the same local network.  
Yes, local. I can see no reason to share storage through clouds or with someone else. We have websites for that.  
> Importer must provide the IP and the password of the original(desktop) storage.
> Once import it complete, the same password is used to unlock storage on the importer app. 

### Where is the storage

#### Desktop
Well, it's a shame, but I just dump it in `composeApp/secured.db` in project root.  

#### Android
On Android it goes under `context.getDatabasePath("secured.db")`, so it's up to android OS to decide where the file is  
stored. In any case, it's sqlcipher database file, so you have little to nothing to worry about.


## Questions and Answers

Q: Where is iOS support?  
A: I don't have practical reasons to implement iOS version since I don't have a device nor intent to test emulators.

Q: Is it possible to store files other than images?  
A: It's a media storage and is 99% intended for images and video.

Q: Wait, why isn't video attachments supported yet?
A: I'm truly sorry, but it appears kotlin multiplatform doesn't have out of box solution for media player like flutter does.  
Due to that I've decided to postpone the implementation until I have more mental capacity to dig through GitHub and docs.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Contributions

Contributions of any sort is welcome.