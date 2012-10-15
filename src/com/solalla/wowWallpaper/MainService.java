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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/*
 * This animated wallpaper draws WoW armory based image
 * http://us.battle.net/static-render/us/dragonmaw/218/84972250-profilemain.jpg
 * 
 */
public class MainService extends WallpaperService {
	static String TAG = "ImageEngine";
	
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
    	
    	//following line I want change to ImageView
        Bitmap pic = BitmapFactory.decodeResource(getResources(), R.drawable.coiler);
        private boolean mVisible = false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainService.this);
        
        
        
        private final Handler mHandler = new Handler();
        private final Runnable mUpdateDisplay = new Runnable() {
        	public void run() {draw();}
        };
        
        
        void draw(){
        	
        	Bitmap rawBgImage = getImage();
        	       	
        	SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    //paint black
                    Paint p = new Paint();
                    p.setColor(Color.BLACK);
                    c.drawRect(0, 0, c.getWidth(), c.getHeight(), p);
                    
                    //Scale the image
                    //Bitmap bgImage = scaleImage(rawBgImage, c);
                    
                    // Draw the background image
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPurgeable = true;                   
                    c.drawBitmap(rawBgImage, 0, 0, null);
                    }
            } finally {
            	holder.unlockCanvasAndPost(c);
            }
            mHandler.removeCallbacks(mUpdateDisplay);
        }
        
        
        Bitmap getImage(){
        	Bitmap img = null;
        	
        	String imageUrl = null;
        	String name = prefs.getString("name", "coiler");
        	String realm = prefs.getString("realm", "dragonmaw");
        	Log.d( TAG, "realm: " +  realm);
        	Log.d( TAG, "name: " + name );
        	
        	String characterUrl = "http://us.battle.net/api/wow/character/"+realm+"/"+name; 
        	Log.d(TAG, "characterUrl: " +characterUrl);
        	
        	
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
        		img =  BitmapFactory.decodeResource(getResources(), R.drawable.coiler);
        	
        	} else {
        		try {
					img = fetchImage(imageUrl);
				} catch (ClientProtocolException e) {
					Log.d(TAG,"Failed to execute httpclient"+e.toString());
					img =  BitmapFactory.decodeResource(getResources(), R.drawable.coiler);
				} catch (IOException e) {
					Log.d(TAG,"Failed to get the image from the response"+e.toString());
					img =  BitmapFactory.decodeResource(getResources(), R.drawable.coiler);
				}
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

            int oldWidth= img.getWidth();
            int oldHeight= img.getHeight();
            float wRatio = ((float) oldWidth) / width;
            float hRatio = ((float) oldHeight) / height;
            
            Matrix m = new Matrix();
            m.postScale(wRatio, hRatio);

            Bitmap out = Bitmap.createBitmap(img, 0, 0, 
                    width, height, m, true);
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
                
        
    } // End ImageEngine class
}
