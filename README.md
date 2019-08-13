# slowmotion camera
Handle high fps video recording 


#### Set FPS
``` kotlin
  camera.setFps(fps:Int)
  ```

## Usage
In your build.gradle, add the following:
``` groovy
  dependencies{
    implementation 'com.sourcepad.opensource:slowmotioncamera:0.1.1@aar'
  }
  
  repositories {
    maven{
        url  "https://sourcepad.bintray.com/opensource"
    }
  }

```

```xml
<com.sourcepad.opensource.slowmotioncamera.CameraView
   android:layout_width="match_parent"
   android:layout_height="match_parent"
  />
```

In your activity/fragment
``` kotlin
  
  override fun onResume(){
     camera.onResume()
  }
  
  override fun onPause(){
     camera.onPause()
  }
  
  ```


