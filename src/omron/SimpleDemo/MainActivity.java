/*
 * Copyright (C) 2014-2015 OMRON Corporation
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

package omron.SimpleDemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import omron.HVC.BleDeviceSearch;
import omron.HVC.HVC;
import omron.HVC.HVC_BLE;
import omron.HVC.HVC_PRM;
import omron.HVC.HVC_RES;
import omron.HVC.HVC_RES.DetectionResult;
import omron.HVC.HVC_RES.FaceResult;
import omron.HVC.HVCBleCallback;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
	private static final String TAG = "CamTestActivity";
	Preview preview;
	//Button buttonClick;
	Camera camera;
	Activity act;
	Context ctx;
	//Interface to global information about an application environment
	//アプリの状態を受け渡すためcontextを渡している
	//Object<-context<-ContextWraper<-ContextThemeWrapper<-Activityの継承関係

    public static final int EXECUTE_STOP = 0;
    public static final int EXECUTE_START = 1;
    public static final int EXECUTE_END = -1;

    private HVC_BLE hvcBle = null;
    private HVC_PRM hvcPrm = null;
    private HVC_RES hvcRes = null;

    private HVCDeviceThread hvcThread = null;

    private static int isExecute = 0;
    private static int nSelectDeviceNo = -1;
    private static List<BluetoothDevice> deviceList = null;
    private static DeviceDialogFragment newFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        hvcBle = new HVC_BLE();
        hvcPrm = new HVC_PRM();
        hvcRes = new HVC_RES();

        hvcBle.setCallBack(hvcCallback);
        hvcThread = new HVCDeviceThread();
        hvcThread.start();
        
        ctx = this;
		act = this;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		//タイトルバーの非表示
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		//フルスクリーン表示

		setContentView(R.layout.main);
		//アクティビティに部品を配置
		//「R.layout.main」はsetContentViewメソッドの引数に指定されている

		preview = new Preview(this, (SurfaceView)findViewById(R.id.surfaceView));
		preview.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		((FrameLayout) findViewById(R.id.layout)).addView(preview);
		preview.setKeepScreenOn(true);
		//おそらく画面が落ちない機能？

		Toast.makeText(ctx, getString(R.string.take_photo_help), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroy() {
        isExecute = EXECUTE_END;
        while ( isExecute == EXECUTE_END );
        if ( hvcBle != null ) {
            try {
                hvcBle.finalize();
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        hvcBle = null;
        super.onDestroy();
    }

    private class HVCDeviceThread extends Thread {
        @Override
        public void run()
        {
            isExecute = EXECUTE_STOP;
            while (isExecute != EXECUTE_END) {
                BluetoothDevice device = SelectHVCDevice("OMRON_HVC.*|omron_hvc.*");
                if ( (device == null) || (isExecute != EXECUTE_START) ) {
                    continue;
                }

                hvcBle.connect(getApplicationContext(), device);
                wait(15);

                hvcPrm.cameraAngle = HVC_PRM.HVC_CAMERA_ANGLE.HVC_CAMERA_ANGLE_0;
                hvcPrm.face.MinSize = 100;
                hvcPrm.face.MaxSize = 400;
                hvcBle.setParam(hvcPrm);
                wait(15);

                while ( isExecute == EXECUTE_START ) {
                    int nUseFunc = //HVC.HVC_ACTIV_BODY_DETECTION |
                                   //HVC.HVC_ACTIV_HAND_DETECTION |
                                   HVC.HVC_ACTIV_FACE_DETECTION |
                                   HVC.HVC_ACTIV_FACE_DIRECTION |
                                   //HVC.HVC_ACTIV_AGE_ESTIMATION |
                                   //HVC.HVC_ACTIV_GENDER_ESTIMATION |
                                   //HVC.HVC_ACTIV_GAZE_ESTIMATION |
                                   //HVC.HVC_ACTIV_BLINK_ESTIMATION |
                                   HVC.HVC_ACTIV_EXPRESSION_ESTIMATION;
                    hvcBle.execute(nUseFunc, hvcRes);
                    wait(30);
                }
                hvcBle.disconnect();
            }
            isExecute = EXECUTE_STOP;
        }

        public void wait(int nWaitCount)
        {
            do {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                if ( !hvcBle.IsBusy() ) {
                    return;
                }
                nWaitCount--;
            } while ( nWaitCount > 0 );
        }
    }

    private BluetoothDevice SelectHVCDevice(String regStr) {
        if ( nSelectDeviceNo < 0 ) {
            if ( newFragment != null ) {
                BleDeviceSearch bleSearch = new BleDeviceSearch(getApplicationContext());
                // Show toast
                showToast("You can select a device");
                while ( newFragment != null ) {
                    deviceList = bleSearch.getDevices();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                bleSearch.stopDeviceSearch(getApplicationContext());
            }

            if ( nSelectDeviceNo > -1 ) {
                // Generate pattern to determine
                Pattern p = Pattern.compile(regStr);
                Matcher m = p.matcher(deviceList.get(nSelectDeviceNo).getName());
                if ( m.find() ) {
                    // Find HVC device
                    return deviceList.get(nSelectDeviceNo);
                }
                nSelectDeviceNo = -1;
            }
            return null;
        }
        return deviceList.get(nSelectDeviceNo);
    }

    private final HVCBleCallback hvcCallback = new HVCBleCallback() {
        @Override
        public void onConnected() {
            // Show toast
            showToast("Selected device has connected");
        }

        @Override
        public void onDisconnected() {
            // Show toast
            showToast("Selected device has disconnected");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Button bt = (Button) findViewById(R.id.button2);
                    bt.setText(R.string.buttonS);
                }
            });
            isExecute = EXECUTE_STOP;
        }

        @Override
        public void onPostSetParam(int nRet, byte outStatus) {
            // Show toast
            String str = "Set parameters : " + String.format("ret = %d / status = 0x%02x", nRet, outStatus);
            showToast(str);
        }

        @Override
        public void onPostGetParam(int nRet, byte outStatus) {
            // Show toast
            String str = "Get parameters : " + String.format("ret = %d / status = 0x%02x", nRet, outStatus);
            showToast(str);
        }

        @Override
        public void onPostExecute(int nRet, byte outStatus) {
        	int expression[]=new int[100];
        	int index = 0;
            if ( nRet != HVC.HVC_NORMAL || outStatus != 0 ) {
                String str = "Execute : " + String.format("ret = %d / status = 0x%02x", nRet, outStatus);
                showToast(str);
            } else {
              
                String str = "Face Detect = " + String.format("%d\n", hvcRes.face.size());
                
                for (FaceResult faceResult : hvcRes.face) {
                
                    if ( (hvcRes.executedFunc & HVC.HVC_ACTIV_EXPRESSION_ESTIMATION) != 0 ) {
                        str += String.format("  [Expression Estimation] : expression = %s, score = %d, degree = %d\n", 
                                                    faceResult.exp.expression == HVC.HVC_EX_NEUTRAL ? "Neutral" ://1
                                                    faceResult.exp.expression == HVC.HVC_EX_HAPPINESS ? "Happiness" ://2
                                                    faceResult.exp.expression == HVC.HVC_EX_SURPRISE ? "Surprise" ://3
                                                    faceResult.exp.expression == HVC.HVC_EX_ANGER ? "Anger" ://4
                                                    faceResult.exp.expression == HVC.HVC_EX_SADNESS ? "Sadness" : "" ,//5
                                                    faceResult.exp.score, faceResult.exp.degree);
                       expression[index] +=HVC.HVC_EX_NEUTRAL;
                       expression[index] +=HVC.HVC_EX_HAPPINESS;
                       expression[index] +=HVC.HVC_EX_SURPRISE;
                       expression[index] +=HVC.HVC_EX_ANGER;
                       expression[index] +=HVC.HVC_EX_SADNESS;
                       
                    		   
                    }
                    index++;
                }
                String text="";
                for(int i =1;i<index;i++){
                       if(expression[i]!=expression[i-1]) {
                    	   text = "表情が違います";
                    	   break;
                       }
                       if(i==index-1){
                    	   text="表情がおなじになりました";
                    	   camera.takePicture(shutterCallback, rawCallback, jpegCallback);
                       }
                }
                final String viewText = str+text;
                       
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView tvVer = (TextView) findViewById(R.id.textView1);
                        tvVer.setText(viewText);
                    }
                });
            }
        }
    };

    public void onClick1(View view) {
        switch (view.getId()){
            case R.id.button1:
                if ( isExecute == EXECUTE_START ) {
                    // Show toast
                    Toast.makeText(this, "You are executing now", Toast.LENGTH_SHORT).show();
                    break;
                }
                nSelectDeviceNo = -1;
                newFragment = new DeviceDialogFragment();
                newFragment.setCancelable(false);
                newFragment.show(getFragmentManager(), "Bluetooth Devices");
                break;
        }
    }

    public void onClick2(View view) {
        switch (view.getId()){
            case R.id.button2:
                if ( nSelectDeviceNo == -1 ) {
                    // Show toast
                    Toast.makeText(this, "You must select device", Toast.LENGTH_SHORT).show();
                    break;
                }
                if ( isExecute == EXECUTE_STOP ) {
                    Button bt = (Button) findViewById(R.id.button2);
                    bt.setText(R.string.buttonE);
                    isExecute = EXECUTE_START;
                } else
                if ( isExecute == EXECUTE_START ) {
                    Button bt = (Button) findViewById(R.id.button2);
                    bt.setText(R.string.buttonS);
                    isExecute = EXECUTE_STOP;
                }
                break;
        }
    }

    public void showToast(final String str) {
        // Show toast
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public class DeviceDialogFragment extends DialogFragment {
        String[] deviceNameList = null;
        ArrayAdapter<String> ListAdpString = null;

        @SuppressLint("InflateParams")
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            LayoutInflater inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View content = inflater.inflate(R.layout.devices, null);
            builder.setView(content);

            ListView listView = (ListView)content.findViewById(R.id.devices);
            // Set adapter
            ListAdpString = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_single_choice); 
            listView.setAdapter(ListAdpString);

            // Set the click event in the list view
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
                /**
                 * It is called when you click on an item
                 */
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    nSelectDeviceNo = position;
                    newFragment = null;
                    dismiss();
                }
            });

            DeviceDialogThread dlgThread = new DeviceDialogThread();
            dlgThread.start();

            builder.setMessage(getString(R.string.button1))
                   .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    newFragment = null;
                }
            });
            // Create the AlertDialog object and return it
            return builder.create();
        }

        private class DeviceDialogThread extends Thread {
            @Override
            public void run()
            {
                do {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if ( ListAdpString != null ) {
                                ListAdpString.clear();
                                if ( deviceList == null ) {
                                    deviceNameList = new String[] { "null" };
                                } else {
                                    synchronized (deviceList) {
                                        deviceNameList = new String[deviceList.size()];

                                        int nIndex = 0;
                                        for (BluetoothDevice device : deviceList) {
                                            if (device.getName() == null ) {
                                                deviceNameList[nIndex] = "no name";
                                            } else {
                                                deviceNameList[nIndex] = device.getName();
                                            }
                                            nIndex++;
                                        }
                                    }
                                }
                                ListAdpString.addAll(deviceNameList);
                                ListAdpString.notifyDataSetChanged();
                            }
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } while(true);
            }
        }
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setTitle(R.string.popup_title)
        .setMessage(R.string.popup_message)
        .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    finish();
                } catch (Throwable e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        })
        .setNegativeButton(R.string.popup_no, null)
        .show();
    }
	@Override
	protected void onResume() {
		super.onResume();
		int numCams = Camera.getNumberOfCameras();
		if(numCams > 0){
			try{
				camera = Camera.open(0);
				camera.startPreview();
				preview.setCamera(camera);
			} catch (RuntimeException ex){
				Toast.makeText(ctx, getString(R.string.camera_not_found), Toast.LENGTH_LONG).show();
			}
		}
	}

	@Override
	protected void onPause() {
		if(camera != null) {
			camera.stopPreview();
			preview.setCamera(null);
			camera.release();
			camera = null;
		}
		super.onPause();
	}

	private void resetCam() {
		camera.startPreview();
		preview.setCamera(camera);
	}

	private void refreshGallery(File file) {
		Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		mediaScanIntent.setData(Uri.fromFile(file));
		sendBroadcast(mediaScanIntent);
	}

	ShutterCallback shutterCallback = new ShutterCallback() {
		public void onShutter() {
			//			 Log.d(TAG, "onShutter'd");
		}
	};

	PictureCallback rawCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			//			 Log.d(TAG, "onPictureTaken - raw");
		}
	};

	PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			new SaveImageTask().execute(data);
			resetCam();
			Log.d(TAG, "onPictureTaken - jpeg");
		}
	};

	private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

		@Override
		protected Void doInBackground(byte[]... data) {
			FileOutputStream outStream = null;

			// Write to SD Card
			try {
				File sdCard = Environment.getExternalStorageDirectory();
				File dir = new File (sdCard.getAbsolutePath() + "/camtest");
				dir.mkdirs();				

				String fileName = String.format("%d.jpg", System.currentTimeMillis());
				File outFile = new File(dir, fileName);

				outStream = new FileOutputStream(outFile);
				outStream.write(data[0]);
				outStream.flush();
				outStream.close();

				Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to " + outFile.getAbsolutePath());

				refreshGallery(outFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
			}
			return null;
		}

	}
    }

