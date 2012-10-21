/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.solalla.wowWallpaper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

/*
 * This animated wallpaper draws WoW armory based image
 * http://us.battle.net/static-render/us/dragonmaw/218/84972250-profilemain.jpg
 * 
 */
public class MainService extends WallpaperService {
	static String TAG = "ImageEngine";
	String lastImageName = "last-image.png";
		
	int updateFreqLong = 6*60*60*1000;
	int updateFreqShort = 60*60*1000;
	
    private final Handler mHandler = new Handler();

    
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public Engine onCreateEngine() {
        return new ImageEngine();
    }
   
    class ImageEngine extends Engine {
    	   	       
        private boolean mVisible = false;
        SharedPreferences prefs;
               
        private final Handler mHandler = new Handler();
        private final Runnable mUpdateDisplay = new Runnable() {
        	public void run() {draw();}
        };
                
        void draw(){
        	Log.d(TAG , "[draw()]");
        	
        	prefs = PreferenceManager.getDefaultSharedPreferences(MainService.this);
        	       	        	
        	long lastUpdated = prefs.getLong("lastUpdated", (long) 0);
        	boolean refresh = prefs.getBoolean("refresh", false);
        	Log.d(TAG, "refresh:" + refresh);
        	
        	boolean needUpdate = false;       	
        	long now = new Date().getTime();
        	
        	
        	Bitmap rawBgImage = null;
        	Bitmap bgImage;
        	
        	SurfaceHolder holder = getSurfaceHolder();
        	Canvas c = holder.lockCanvas();
        	
        	// Determine if we need an update based on whether we have a stored image or its out of date
        	if (lastUpdated == (long) 0 || now - lastUpdated > updateFreqLong || refresh) {
        		needUpdate = true;
        		prefs.edit().putBoolean("refresh", false).commit();
        	        		
        	}
        	
        	Log.d(TAG, "needUpdate: " + needUpdate);
        	
        	if (haveNetwork() && needUpdate){
        		// We have a network and we need an update so fetch and scale image
        		rawBgImage = fetchImage();
        	} else {
        		// Get the old image        		
        		rawBgImage = getLocalImage();	
            }
        	bgImage = scaleImage(rawBgImage, c);
        	// Final catch
        	if (bgImage == null) {
        		Log.d(TAG,"Could not find an image. Using default");
        		rawBgImage = BitmapFactory.decodeResource(getResources(), R.drawable.not_found);
        		bgImage = scaleImage(rawBgImage, c);
        	}
        	
        	try {
        		if (c != null) {
                    //paint black
                    Paint p = new Paint();
                    p.setColor(Color.BLACK);
                    c.drawRect(0, 0, c.getWidth(), c.getHeight(), p);
                                       
                    // Draw the background image
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPurgeable = true;                   
                    c.drawBitmap(bgImage, 0, 0, null);
                    }
            } finally {
            	holder.unlockCanvasAndPost(c);
            }
            mHandler.removeCallbacks(mUpdateDisplay);
            mHandler.postDelayed(mUpdateDisplay, updateFreqLong);
            
        }
         
        Bitmap getLocalImage(){
        	Bitmap img = null;
        	
        	File dir = MainService.this.getFilesDir();
        	Log.d(TAG,"Trying to load: " + dir + "/"+ lastImageName);
        	img = BitmapFactory.decodeFile(dir + "/"+ lastImageName);
        	return img;
        }
        
        Bitmap fetchImage(){
        	Bitmap img = null;
        	
        	String imageUrl = null;
        	String name = prefs.getString("name", "coiler");
        	String realm = prefs.getString("realm", "dragonmaw");
        	//Log.d( TAG, "realm: " +  realm);
        	//Log.d( TAG, "name: " + name );
        	
        	String characterUrl = "http://us.battle.net/api/wow/character/"+realm+"/"+name; 
        	//Log.d(TAG, "characterUrl: " +characterUrl);
        	        	
			try {
				imageUrl = fetchImageUrl(characterUrl);
			} catch (ClientProtocolException e) {
				Log.d(TAG,"Failed to execute httpclient"+e.toString());
			} catch (IOException e) {
				Log.d(TAG,"Failed to fetch image url "+e.toString());
			} catch (JSONException e) {
				Log.d(TAG,"Could not parse JSON from WoW "+e.toString());
			}
        	Log.d(TAG, "imageUrl: " +imageUrl);
        	        	
        	if (imageUrl == null){
        		img =  BitmapFactory.decodeResource(getResources(), R.drawable.not_found);
        	
        	} else {
        		try {
					img = fetchImage(imageUrl);
				} catch (ClientProtocolException e) {
					Log.d(TAG,"Failed to execute httpclient"+e.toString());
					img =  BitmapFactory.decodeResource(getResources(), R.drawable.not_found);
				} catch (IOException e) {
					Log.d(TAG,"Failed to get the image from the response"+e.toString());
					img =  BitmapFactory.decodeResource(getResources(), R.drawable.not_found);
				}
        	}
        	if (img != null){
        		
        		// Save locally
        		FileOutputStream fos = null;
        		try {
					fos = openFileOutput(lastImageName, MainService.this.MODE_PRIVATE);					
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					Log.e(TAG,"Could not find image file: "+ lastImageName);
					Log.e(TAG,e.toString());
				}
        		
        		if (fos != null){
	        		img.compress(Bitmap.CompressFormat.PNG, 100, fos);  
					try {
						fos.close();
					} catch (IOException e) {
						Log.e(TAG,"Could not write file" + e.toString());
					}
        		}
        		
        		// Update timestamp
        		prefs.edit().putLong("lastUpdated", new Date().getTime()).commit();
        	}
        	
        	return img;
        }
                
        public String fetchImageUrl(String url) throws ClientProtocolException, IOException, JSONException{
        	        	
        	Log.d(TAG,"In connect()");
        	HttpGet httpget = null;
        	HttpResponse response;
        	InputStream instream = null;
        	String result = "";
        	JSONObject jArray = null;
        	
        	HttpClient httpclient = new DefaultHttpClient();
            try {
            	// TODO add url encoding
            	httpget = new HttpGet(url); 
            } catch (IllegalArgumentException e) {
            	Log.e(TAG,"Bad string format for url: " + url + e.toString());
            	return null;
            }
            // Execute the request
            response = httpclient.execute(httpget);
                           
            // If the response does not enclose an entity, there is no need
            // to worry about connection release
            HttpEntity entity = response.getEntity();
            if (entity != null) {
            	instream = entity.getContent();
            }
                     
            //convert response to string
    		BufferedReader reader = new BufferedReader(new InputStreamReader(instream,"iso-8859-1"),8);
    		StringBuilder sb = new StringBuilder();
    		String line = null;
    		while ((line = reader.readLine()) != null) {
    			sb.append(line + "\n");
    		}
    		instream.close();
    		result=sb.toString();
        	
        	//try parse the string to a JSON object
    		jArray = new JSONObject(result);
        	        	
        	String thumbnail = jArray.getString("thumbnail");
        			        	        	
        	String[] tmp = thumbnail.split("-");
        	thumbnail = tmp[0] + "-profilemain.jpg";
        	
        	String imageUrl = "http://us.battle.net/static-render/us/"+thumbnail;
        	Log.d(TAG,"imageUrl: " + imageUrl );
        	return imageUrl;
            
        }
        private Bitmap fetchImage(String url) throws ClientProtocolException, IOException
        {
            Bitmap bitmap = null;
            InputStream instream = null;
            
            HttpClient httpclient = new DefaultHttpClient();

            // Prepare a request object
            HttpGet httpget = new HttpGet(url); 

            // Execute the request
            HttpResponse response;
            
            response = httpclient.execute(httpget);
            // Examine the response status
            Log.i("Praeda",response.getStatusLine().toString());

            // Get hold of the response entity
            HttpEntity entity = response.getEntity();
            // If the response does not enclose an entity, there is no need
            // to worry about connection release

            if (entity != null) {
                // A Simple JSON Response Read
                instream = entity.getContent();
                bitmap = BitmapFactory.decodeStream(instream);
                instream.close();                
            }
          
            return bitmap;
        }
        
        
        private Bitmap scaleImage(Bitmap img, Canvas c){
        	        	
            int width = c.getWidth();
            int height = c.getHeight();
            //Log.d(TAG,"Canvas H x W: " + height + " x "+ width);
                        
            int oldWidth= img.getWidth();
            int oldHeight= img.getHeight();
            //Log.d(TAG,"Image H x W: " + oldHeight + " x "+ oldWidth);
            
            float aspectRatio = ((float) oldWidth) / oldHeight;
                        
            int newHeight = height;
            int newWidth = (int) Math.floor( newHeight * aspectRatio);
            //Log.d(TAG,"New Image H x W: " + newHeight + " x "+ newWidth);
                       
            Bitmap scaled = Bitmap.createScaledBitmap(img, newWidth, newHeight, false);      
            //Log.d(TAG, "Scaled width: "+scaled.getWidth());
            
            int startX = (int) Math.floor(newWidth * 0.25);
            newWidth = (int) Math.floor(newWidth * 0.50);
            //Log.d(TAG,"startX : " + startX + " newWidth: "+ newWidth);
                                
            Bitmap out = Bitmap.createBitmap(scaled, startX, 0, newWidth, newHeight); 
            
            return out;
        }
        
        @Override
        public void onVisibilityChanged(boolean visible) {
        	mVisible = visible;
        	if (visible) {
        		draw();
        	} else {
        		mHandler.removeCallbacks(mUpdateDisplay);
        	}
        }
        
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        	draw();
        }
        
        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
        	super.onSurfaceDestroyed(holder);
        	mVisible = false;
        	mHandler.removeCallbacks(mUpdateDisplay);
        }
        
        @Override
        public void onDestroy() {
	        super.onDestroy();
	        mVisible = false;
	        mHandler.removeCallbacks(mUpdateDisplay);
        }
        
        public boolean haveNetwork() {
        	ConnectivityManager connectivity = (ConnectivityManager) MainService.this.getSystemService(Context.CONNECTIVITY_SERVICE);
	        if (connectivity != null) {
	            NetworkInfo[] info = connectivity.getAllNetworkInfo();
	            if (info != null) {
	                for (int i = 0; i < info.length; i++) {
	                    if (info[i].getState() == NetworkInfo.State.CONNECTED) {
	                    	Log.d(TAG, "You are connected to a network");	
	                    	return true;
	                    }
	                }
	            }
	        }
	        Log.d(TAG, "Not connected to network");
	        return false;
        }
        
    } // End ImageEngine class
}
