/*
 * Copyright (C) 2012 The Android Open Source Project
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


//@TODO: compare coarse locations, could include if user phone orientation, if face down, likely to have lower signal
// make into km, 2 DOP
// get error - compare all data with GPS
	
	package com.example.mapdemo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This shows how UI settings can be toggled.
 */
public class UiSettingsDemoActivity extends FragmentActivity implements LocationListener {
    private GoogleMap mMap;
    private UiSettings mUiSettings;
    
    //private float cellTowerLat = 53.1619;
    //private float cellTowerLng = -9.0256;
    
    
    private LatLng cellTowerLoc = new LatLng(53.269, -9); //galway
    //private static final LatLng cellTowerLoc = new LatLng(53.2, -6.15); //Dublin
    
	private LocationManager lm;
	private TelephonyManager tlm;
	private int RSSI = -1;
	private int CID = -1;
	private int LAC = -1;
	private int MCC = -1;
	private int MNC = -1;	
	private double cellLatitude = -1;
	private double cellLongitude = -1;
	private double prevCellLatitude = -1;
	private double prevCellLongitude = -1;
	private double gpsLatitude = -1;
	private double gpsLongitude = -1;
	private double glsLatitude = -1;
	private double glsLongitude = -1;
	private double lvcLatitude = -1;
	private double lvcLongitude = -1;
	private double GPSdist = -1;
	private double GLSdist = -1;
	private double LVCdist = -1;
	private double GLSerror = -1;
	private double LVCerror = -1;
	private int refreshTime = 2000;
	private Context context;
    private Circle cCurrCellTowerLoc;
    private Marker mCurrCellTowerLoc; 
    private Marker mPrevCellTowerLoc; 


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ui_settings_demo);
        setUpMapIfNeeded();
        setUpLocationManagers();
        addMarkersToMap();		///ADDED
        mMap.getUiSettings().setZoomControlsEnabled(false);
        
        
	    /****phone network data**/
	    PhoneStateListener phoneStateListener = new PhoneStateListener() {
	    	public void onCallForwardingIndicatorChanged(boolean cfi) {}
	    	public void onCallStateChanged(int state, String incomingNumber) {}
	    	public void onCellLocationChanged(CellLocation location) {	
	    		
	    		
	    	    CID = ((GsmCellLocation) location).getCid();
	    	    LAC = ((GsmCellLocation) location).getLac();
	    	    
	    	    updateCellLocation(CID, LAC);
	    	    
	    	    //check if the result is valid
				if(cellLatitude != -1){
					updateDistance();
					updateLVC();
				    updateCsvFile();
		    		updateDisplay();	
				}

	    	}
	    	public void onDataActivity(int direction) {}
	    	public void onDataConnectionStateChanged(int state) {}
	    	public void onMessageWaitingIndicatorChanged(boolean mwi) {}
	    	public void onServiceStateChanged(ServiceState serviceState) {}
	    	public void onSignalStrengthChanged(int asu) {		
	    		
	    		RSSI = (asu*2)-113;
				updateLVC();
			    updateCsvFile();
	    		updateDisplay();
	    	    //http://en.wikipedia.org/wiki/Mobile_phone_signal#ASU	    		
	    	}
	    };

		tlm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		//GsmCellLocation cellLocation = (GsmCellLocation) tlm.getCellLocation();
	       
	       
	    String networkOperator = tlm.getNetworkOperator();

	    if (networkOperator != null) {
	        MCC = Integer.parseInt(networkOperator.substring(0, 3));
	        MNC = Integer.parseInt(networkOperator.substring(3));
	    }
		


		tlm.listen(phoneStateListener,
				PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR |
				PhoneStateListener.LISTEN_CALL_STATE |
				PhoneStateListener.LISTEN_CELL_LOCATION |
				PhoneStateListener.LISTEN_DATA_ACTIVITY |
				PhoneStateListener.LISTEN_DATA_CONNECTION_STATE |
				PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR |
				PhoneStateListener.LISTEN_SERVICE_STATE |
				PhoneStateListener.LISTEN_SIGNAL_STRENGTH
		);
		//http://android-coding.blogspot.ie/2011/06/convert-celllocation-to-real-location.html
		
    }
    
    private void updateCellLocation(int CID, int LAC) {
    	
	    //backup previous cell
		prevCellLatitude = cellLatitude;
		prevCellLongitude = cellLongitude;
	    
		//find location of new cell
		if(CID != -1 && LAC != -1) {
		    int cellInfo[] = {CID, LAC};    	    
		    new RetreiveCellLocationTask().execute(cellInfo);   			
		}
    }
    
    private void updateDistance() {
    	
		if (prevCellLatitude == -1) {
			prevCellLatitude = cellLatitude;
			prevCellLongitude = cellLongitude;					
		}
		
		double lat1 = cellLatitude;
	    double lng1 = cellLongitude;
	    double lat2 = gpsLatitude;
	    double lng2 = gpsLongitude;
	    float [] dist = new float[1];
	    Location.distanceBetween(lat1, lng1, lat2, lng2, dist);
	    GPSdist = dist[0];
	
		lat1 = cellLatitude;
	    lng1 = cellLongitude;
	    lat2 = prevCellLatitude;
	    lng2 = prevCellLongitude;
	    Location.distanceBetween(lat1, lng1, lat2, lng2, dist);
	    LVCdist = dist[0];	//distance between the towers
	    
	    
		lat1 = cellLatitude;
	    lng1 = cellLongitude;
	    lat2 = glsLatitude;
	    lng2 = glsLongitude;
	    Location.distanceBetween(lat1, lng1, lat2, lng2, dist);
	    GLSdist = dist[0];	//distance between the towers
	    
	    
	    //errors:
		lat1 = glsLatitude;
	    lng1 = glsLongitude;
	    lat2 = gpsLatitude;
	    lng2 = gpsLongitude;
	    dist = new float[1];
	    Location.distanceBetween(lat1, lng1, lat2, lng2, dist);
	    GLSerror = dist[0];
	    
		lat1 = lvcLatitude;
	    lng1 = lvcLongitude;
	    lat2 = gpsLatitude;
	    lng2 = gpsLongitude;
	    dist = new float[1];
	    Location.distanceBetween(lat1, lng1, lat2, lng2, dist);
	    LVCerror = dist[0];	    
    }
    
    private void updateLVC() {
    //function calculates the users location for a given RSSI, cell location, previous cell location
    	//http://developer.android.com/reference/android/telephony/NeighboringCellInfo.html
    /*
    	RSSI
		lat1 = cellLatitude;
	    lng1 = cellLongitude;
	    lat2 = prevCellLatitude;
	    lng2 = prevCellLongitude;   

    	*/
    	
    	//max rssi = 116 -31*2 = -51
    	
    //distance between 2 cell towers = 
    	//find point between 2 cell towers
    	//http://en.wikipedia.org/wiki/Geographic_coordinate_system
    	
    	
    	//http://www.geodatasource.com/developers/java
    	/*
    	lvcLatitude = cellLatitude;
    	lvcLongitude = cellLongitude;
    	*/
    	lvcLatitude =  cellLatitude  + ((cellLatitude  - prevCellLatitude )/2);
    	lvcLongitude = cellLongitude + ((cellLongitude - prevCellLongitude)/2);
    
    }

    
    
    
    
    private void updateDisplay() {
    	
    	LatLng cellTower = new LatLng(cellLatitude, cellLongitude);
        cCurrCellTowerLoc.setCenter(cellTower);
        mCurrCellTowerLoc.setPosition(cellTower);
		mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(cellTower, 12.0f));

    	LatLng prevCellTower = new LatLng(prevCellLatitude, prevCellLongitude);
    	mPrevCellTowerLoc.setPosition(prevCellTower);
		
		
		TextView t= new TextView(context); 
		
	    t=(TextView)findViewById(R.id.cellInfoText);
	    t.setText(" LAC: " + LAC + " MCC: " + MCC + " MNC: " + MNC + " CID: " + CID);

	    t=(TextView)findViewById(R.id.CTLocText);
		if(cellLatitude == -1){ 
			t.setText(" CT: Cannot find location");
		} else {
			t.setText(" CT: " + String.format("%.6f", cellLatitude) + ", " + String.format("%.6f", cellLongitude));
		}
		
	    t=(TextView)findViewById(R.id.GPSdistText); 
	    t.setText(" GPSdist: " + String.format("%.2f", GPSdist) + " m");
	
	    t=(TextView)findViewById(R.id.GLSdistText); 
	    t.setText(" GLSdist: " + String.format("%.2f", GLSdist) + " m");	    
	       
	    t=(TextView)findViewById(R.id.LVCdistText); 
	    t.setText(" LVCdist: " + String.format("%.2f", LVCdist) + " m");
	
	    t=(TextView)findViewById(R.id.RSSIText);
	    t.setText(" RSSI: " + RSSI + " dBm");
	    
	    t=(TextView)findViewById(R.id.GPSLocText);			
	    t.setText(" GPS: " + String.format("%.6f", gpsLatitude) + ", " + String.format("%.6f", gpsLongitude));
	    
	    t=(TextView)findViewById(R.id.GLSLocText);			
	    t.setText(" GLS: " + String.format("%.6f", glsLatitude) + ", " + String.format("%.6f", glsLongitude));	
	    
	    t=(TextView)findViewById(R.id.LVCLocText);			
	    t.setText(" LVC: " + String.format("%.6f", lvcLatitude) + ", " + String.format("%.6f", lvcLongitude));
	    
	    
	    /*****Error*/
	    t=(TextView)findViewById(R.id.GLSErrText);			
	    t.setText(" GLSerr: " + String.format("%.2f", GLSerror) + " m");	
	    
	    t=(TextView)findViewById(R.id.LVCErrText);			
	    t.setText(" LVCerr: " + String.format("%.2f", LVCerror) + " m");
    }
    
    private void addMarkersToMap() {
        // Uses a colored icon.
        mCurrCellTowerLoc = mMap.addMarker(new MarkerOptions()
                .position(cellTowerLoc)
                .title("cellTowerLoc")
                .snippet("Signal Strength: -71dB")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

       
        // Get back the mutable Circle
        cCurrCellTowerLoc = mMap.addCircle(new CircleOptions()
	        .center(cellTowerLoc)
	        .radius(3000) // In meters
	        .strokeWidth(0)
	        .fillColor(Color.HSVToColor(100, new float[] {240, 1, 1}))
        );
        
        
        
        // Uses a colored icon.
        mPrevCellTowerLoc = mMap.addMarker(new MarkerOptions()
                .position(cellTowerLoc)
                .title("cellTowerLoc")
                .snippet("Signal Strength: -71dB")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)));
        
    }   
    
    
    private void setUpLocationManagers() {  
		context = getApplicationContext();
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, refreshTime, 0, this);
		lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, refreshTime, 0, this);
		
		
		if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
		{
		     Log.i("PROVIDER", "ENABLED");
		}
		else {
			Log.i("PROVIDER", "DISABLED");		
		}
    }
	
	
    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    
	public void onLocationChanged(Location loc) {

		if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)) {
			gpsLatitude = loc.getLatitude();
			gpsLongitude = loc.getLongitude();			
		}
		
		if (loc.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
			glsLatitude = loc.getLatitude();
			glsLongitude = loc.getLongitude();
			/*
			CharSequence text = "GSM: Lat: " + loc.getLatitude() + "\nLong: " + loc.getLongitude();
			int duration = Toast.LENGTH_SHORT;
			Toast toast = Toast.makeText(context, text, duration);
			toast.show();*/
		}

		updateDistance();
		updateDisplay();
	    /* TOAST
		CharSequence text = "Lat: " + loc.getLatitude() + "\nLong: " + loc.getLongitude() + "\nDISTANCE: " + dist[0];
		int duration = Toast.LENGTH_SHORT;
		Toast toast = Toast.makeText(context, text, duration);
		toast.show();*/
		
	}
	
	
    class RetreiveCellLocationTask extends AsyncTask<int[], Void, int[]> {
		@Override
		protected int[] doInBackground(int[]... arg0) {
			RqsLocation(CID, LAC);
			return null;
		}
    }
    
    private Boolean RqsLocation(int cid, int lac){

        Boolean result = false;
        String urlmmap = "http://www.google.com/glm/mmap";
        
        try {
            
            URL url = new URL(urlmmap);
            URLConnection conn = url.openConnection();
            HttpURLConnection httpConn = (HttpURLConnection) conn;
            httpConn.setRequestMethod("POST");
            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);
            httpConn.connect();
            
            OutputStream outputStream = httpConn.getOutputStream();
            WriteData(outputStream, cid, lac);
            
            InputStream inputStream = httpConn.getInputStream();
            DataInputStream dataInputStream = new DataInputStream(inputStream);
            
            dataInputStream.readShort();
            dataInputStream.readByte();
            int code = dataInputStream.readInt();
            if (code == 0) {
                cellLatitude = (float) dataInputStream.readInt()/1000000;
                cellLongitude = (float) dataInputStream.readInt()/1000000;
				//if it's the first sample (initial values haven't been changed) previous=current
				if (prevCellLatitude == -1) {
					prevCellLatitude = cellLatitude;
					prevCellLongitude = cellLongitude;					
				}
                
                result = true;
            } else {
				CharSequence text = "Cell location finder error";
				int duration = Toast.LENGTH_SHORT;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();           		
            }
        }
        catch (IOException e) {
            e.printStackTrace();  
        }
        return result;
    }
    
    //http://stackoverflow.com/questions/6343166/android-os-networkonmainthreadexception
    private void WriteData(OutputStream out, int cid, int lac)
    		  throws IOException		  
    {    
    	DataOutputStream dataOutputStream = new DataOutputStream(out);
    	dataOutputStream.writeShort(21);
    	dataOutputStream.writeLong(0);
    	dataOutputStream.writeUTF("en");
    	dataOutputStream.writeUTF("Android");
    	dataOutputStream.writeUTF("1.0");
    	dataOutputStream.writeUTF("Web");
    	dataOutputStream.writeByte(27);
    	dataOutputStream.writeInt(0);
    	dataOutputStream.writeInt(0);
    	dataOutputStream.writeInt(3);
    	dataOutputStream.writeUTF("");
    		 
    	dataOutputStream.writeInt(cid);
    	dataOutputStream.writeInt(lac);   
    		 
    	dataOutputStream.writeInt(0);
    	dataOutputStream.writeInt(0);
    	dataOutputStream.writeInt(0);
    	dataOutputStream.writeInt(0);
    	dataOutputStream.flush();    	
    }

 

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	@Override
	public void onProviderDisabled(String arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onProviderEnabled(String arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		// TODO Auto-generated method stub
		
	}

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        mMap.setMyLocationEnabled(true);
        mUiSettings = mMap.getUiSettings();
    }

    /**
     * Checks if the map is ready (which depends on whether the Google Play services APK is
     * available. This should be called prior to calling any methods on GoogleMap.
     */
    private boolean checkReady() {
        if (mMap == null) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    } 
    
    
    


    
    
        
    private void updateCsvFile()
    {
    	/*
    	 * 	private LocationManager lm;
    	private TelephonyManager tlm;
    	private int RSSI = -1;
    	private int CID = -1;
    	private int LAC = -1;
    	private int MCC = -1;
    	private int MNC = -1;	
    	private double cellLatitude = -1;
    	private double cellLongitude = -1;
    	private double prevCellLatitude = -1;
    	private double prevCellLongitude = -1;
    	private double gpsLatitude = -1;
    	private double gpsLongitude = -1;
    	private double glsLatitude = -1;
    	private double glsLongitude = -1;
    	private double lvcLatitude = -1;
    	private double lvcLongitude = -1;
    	private double GPSdist = -1;
    	private double GLSdist = -1;
    	private double LVCdist = -1;
    	private double GLSerror = -1;
    	private double LVCerror = -1;

    	 */
    	
        try
        {
        	String sFileName = "LVCdata.csv";
            File root = Environment.getExternalStorageDirectory();
            File gpxfile = new File(root, sFileName);
            FileWriter writer = new FileWriter(gpxfile, true);
            
	        writer.append(
	        	System.currentTimeMillis() + "," +
	        	new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()) + "," +
		        gpsLatitude + "," +
		        gpsLongitude + "," +
		        glsLatitude + "," +
		        glsLongitude + "," +
		        GLSerror + "," +
		        CID + "," +
		        LAC + "," +
		        MCC + "," +
		        MNC + "," +
		        RSSI + "," +
		        cellLatitude + "," +
		        cellLongitude + "," +
		        prevCellLatitude + "," +
		        prevCellLongitude + "," +
		        GPSdist + "," +
		        GLSdist + "," +
		        lvcLatitude + "," +
		        lvcLongitude + "," +
		        LVCdist + "," +
		        LVCerror + "\r\n"
	        );
	        
	        writer.flush();
	        writer.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        } 

     }

    
    
}



