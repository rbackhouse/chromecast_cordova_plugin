/*
* The MIT License (MIT)
* 
* Copyright (c) 2013 Richard Backhouse
* 
* Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
* to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
* and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
* 
* The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
* DEALINGS IN THE SOFTWARE.
*/
package org.potpie.cordova.plugins.chromecast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;

import com.google.cast.ApplicationChannel;
import com.google.cast.ApplicationMetadata;
import com.google.cast.ApplicationSession;
import com.google.cast.CastContext;
import com.google.cast.CastDevice;
import com.google.cast.MediaProtocolCommand;
import com.google.cast.MediaProtocolMessageStream;
import com.google.cast.MediaProtocolMessageStream.PlayerState;
import com.google.cast.MediaRouteAdapter;
import com.google.cast.MediaRouteHelper;
import com.google.cast.MediaRouteStateChangeListener;
import com.google.cast.SessionError;
import com.google.cast.Logger;

public class ChromecastPlugin extends CordovaPlugin implements MediaRouteAdapter {
	private final String APP_ID = "ece06762-f097-4ab4-9b82-adb57ed36330";
	
	private CastContext castContext = null;
    private CastDevice selectedDevice = null;
    private ApplicationSession session = null;
    private MediaProtocolMessageStream messageStream = null;
    private MediaRouter mediaRouter = null;
    private MediaRouteSelector mediaRouteSelector = null;
    private MediaRouter.Callback mediaRouterCallback = null;
    private List<RouteInfo> routes = null;
    private CallbackContext receiverCallback = null;
    private CallbackContext statusCallback = null;

	public ChromecastPlugin() {
		routes = new ArrayList<RouteInfo>();
	}
	
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		//Logger.setDebugEnabledByDefault(true);
		receiverCallback = null;
		statusCallback = null;
		castContext = new CastContext(cordova.getActivity().getApplicationContext());
		MediaRouteHelper.registerMinimalMediaRouteProvider(castContext, this);
		mediaRouter = MediaRouter.getInstance(cordova.getActivity().getApplicationContext());
		mediaRouteSelector = MediaRouteHelper.buildMediaRouteSelector(MediaRouteHelper.CATEGORY_CAST, APP_ID, null);
		mediaRouterCallback = new MediaRouterCallback();
		mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
		
        Runnable runnable = new StatusRunner();
        new Thread(runnable).start();		
	}

	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (action.equals("startReceiverListener")) {
			receiverCallback = callbackContext;
			PluginResult result = new PluginResult(PluginResult.Status.OK, new JSONObject());
			result.setKeepCallback(true);
			receiverCallback.sendPluginResult(result);
	        return true;
		} else if (action.equals("setReceiver")) {
			int index = args.getInt(0);
			try {
				RouteInfo route = routes.get(index);
				System.out.println("route :"+index+" "+route.getId()+" selected");
				mediaRouter.selectRoute(route);
				callbackContext.success();
		        return true;
			} catch (IndexOutOfBoundsException e) {
				callbackContext.error("Receiver not found");
		        return false;
			}
		} else if (action.equals("cast")) {
			String mediaurl = args.getString(0);
			try {
				System.out.println("cast :"+mediaurl);
				startCast(mediaurl);
				callbackContext.success();
		        return true;
			} catch (IOException e) {
				callbackContext.error("cast failed :"+e.getMessage());
		        return false;
			}
		} else if (action.equals("pause")) {
			try {
				System.out.println("pause");
				pause();
				callbackContext.success();
				return true;
			} catch (IOException e) {
				callbackContext.error("pause failed :"+e.getMessage());
		        return false;
			}
		} else if (action.equals("play")) {
			try {
				int position = args.getInt(0);
				System.out.println("play :"+position);
				play(position);
				callbackContext.success();
				return true;
			} catch (IOException e) {
				callbackContext.error("play failed :"+e.getMessage());
		        return false;
			}
		} else if (action.equals("stopCast")) {
			try {
				System.out.println("stopCast");
				stopCast();
				callbackContext.success();
		        return true;
			} catch (IOException e) {
				callbackContext.error("stop cast failed :"+e.getMessage());
		        return false;
			}
		} else if (action.equals("startStatusListener")) {
		    statusCallback = callbackContext;
			callbackContext.sendPluginResult(getStatus(null));
			return true;
		} else {
			callbackContext.error("Invalid action");
	        return false;
		}
	}

	public void onDeviceAvailable(CastDevice castDevice, String deviceName, MediaRouteStateChangeListener listener) {
		selectedDevice = castDevice;
	}
	
	private void startCast(String mediaUrl) throws IOException {
		if (session != null) {
			stopCast();
		}
		session = new ApplicationSession(castContext, selectedDevice);
		session.setApplicationOptions(0);
		final String url = mediaUrl;
		session.setListener(new ApplicationSession.Listener() {
			public void onSessionEnded(SessionError sessionError) {
				System.out.println("Session ended :"+sessionError);
    			if (statusCallback != null) {
    				statusCallback.sendPluginResult(getStatus("Session ended : "+ (sessionError == null ? "" : sessionError.toString())));
            	}
			}

			public void onSessionStartFailed(SessionError sessionError) {
				System.out.println("Session start failed :"+sessionError);
    			if (statusCallback != null) {
    				statusCallback.sendPluginResult(getStatus("Session start failed : "+sessionError));
            	}
			}

			public void onSessionStarted(ApplicationMetadata applicationMetadata) {
				System.out.println("Session started");
				ApplicationChannel channel = session.getChannel();
				messageStream = new MediaProtocolMessageStream();
                channel.attachMessageStream(messageStream);
                
                if (messageStream.getPlayerState() == null) {
            		try {
        				System.out.println("Session loadMedia :"+url);
            			MediaProtocolCommand cmd = messageStream.loadMedia(url, null, true);
	                    cmd.setListener(new MediaProtocolCommand.Listener() {
	                        public void onCompleted(MediaProtocolCommand cmd) {
	            				System.out.println("load complete :"+url);
	                			if (statusCallback != null) {
	                				statusCallback.sendPluginResult(getStatus("load complete :"+url));
	                        	}
	                        }
	                        public void onCancelled(MediaProtocolCommand cmd) {
	            				System.out.println("load cancelled :"+url);
	                			if (statusCallback != null) {
	                				statusCallback.sendPluginResult(getStatus("load cancelled :"+url));
	                        	}
	                        }
	                    });                	
            		} catch (IOException e) {
        				System.out.println("load exception :"+e.getMessage());
            			e.printStackTrace();
            			if (statusCallback != null) {
            				statusCallback.sendPluginResult(getStatus("load exception :"+e.getMessage()));
                    	}
            		}
                } else {
    				System.out.println("player state :"+messageStream.getPlayerState());
                }                
			}
		});
		session.startSession(APP_ID);
	}
	
	private void pause() throws IOException {
		if (messageStream.getPlayerState() == PlayerState.PLAYING) {
			messageStream.stop();
		}
	}
	
	private void play(int position) throws IOException {
		System.out.println("Player State :"+messageStream.getPlayerState());
		if (messageStream.getPlayerState() == PlayerState.STOPPED) {
			messageStream.resume();
		} else {
			messageStream.playFrom((double)position);
		}
	}
	
	private void stopCast() throws IOException {
		if (messageStream != null) {
			messageStream.stop();
			session.endSession();
			messageStream = null;
			session = null;
		}
	}
	
	private PluginResult getStatus(String statusMessage) {
		JSONObject status = new JSONObject();
		try {
			if (messageStream != null) {
				status.put("state", messageStream.getPlayerState().toString());
				status.put("position", messageStream.getStreamPosition());
				status.put("duration", messageStream.getStreamDuration());
			} else {
				status.put("state", "");
				status.put("position", 0.0);
				status.put("duration", 0.0);
			}
			if (statusMessage != null) {
				status.put("statusMessage", statusMessage);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		PluginResult result = new PluginResult(PluginResult.Status.OK, status);
		result.setKeepCallback(true);
		return result;
	}

	public void onSetVolume(double volume) {
	}

	public void onUpdateVolume(double volume) {
	}
	
	public void onDestroy() {
		super.onDestroy();
		castContext.dispose();
		castContext = null;
	}

	public void onPause(boolean multitasking) {
		super.onPause(multitasking);
	}

	public void onReset() {
		super.onReset();
	}

	public void onResume(boolean multitasking) {
		super.onResume(multitasking);
		if (session != null && session.isResumable()) {
			try {
				session.resumeSession();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class MediaRouterCallback extends MediaRouter.Callback {
		public void onRouteAdded(MediaRouter router, RouteInfo route) {
			super.onRouteAdded(router, route);
			routes.add(route);
			System.out.println("route added :"+route.getId()+":"+route.getName()+":"+route.getDescription());
			if (receiverCallback != null) {
				JSONObject jsonRoute = new JSONObject();
				try {
					jsonRoute.put("id", route.getId());
					jsonRoute.put("name", route.getName());
					jsonRoute.put("description", route.getDescription());
					jsonRoute.put("index", routes.size() - 1);
				} catch (JSONException e) {
					
				}
				PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRoute);
				result.setKeepCallback(true);
				receiverCallback.sendPluginResult(result);
			}
		}
		public void onRouteRemoved(MediaRouter router, RouteInfo route) {
			super.onRouteRemoved(router, route);
			System.out.println("route removed :"+route.getId()+":"+route.getName()+":"+route.getDescription());
			routes.remove(route);
		}
		public void onRouteSelected(MediaRouter router, RouteInfo route) {
			System.out.println("route selected :"+route.getName());
            MediaRouteHelper.requestCastDeviceForRoute(route);
        }
        public void onRouteUnselected(MediaRouter router, RouteInfo route) {
        }
	}
	
    private class StatusRunner implements Runnable {
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
        			if (statusCallback != null) {
        				statusCallback.sendPluginResult(getStatus(null));
                	}
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }
        }
    }
}
