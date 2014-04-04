package pl.itraff.recognize.TestApi;

import java.io.ByteArrayOutputStream;

import pl.itraff.TestApi.ItraffApi.ItraffApi;
import pl.itraff.TestApi.ItraffApi.model.*;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	static Boolean debug = false;

	// only for demo to enable user to edit this values on screen
	private String CLIENT_API_KEY = null;
	private Integer CLIENT_API_ID = null;

	public static final String ID = "id";
	public static final String KEY = "key";

	// tag for debug
	private static final String TAG = "TestApi";
	public static final String PREFS_NAME = "iTraffTestApiPreferences";

	private static final int RESULT_BMP_DAMAGED = 128;

	private TextView responseView;

	private EditText clientIdEdit;
	private EditText clientApiKeyEdit;

	// minimum size of picture to scale
	final int REQUIRED_SIZE = 400;

	ProgressDialog waitDialog;

	protected APIResponse response;

	private Button imageShowBtn;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		responseView = (TextView) findViewById(R.id.response_text);

		clientIdEdit = (EditText) findViewById(R.id.client_id_edit);
		clientApiKeyEdit = (EditText) findViewById(R.id.client_api_key_edit);

		// Restore preferences
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		CLIENT_API_ID = settings.getInt(ID, 0);
		CLIENT_API_KEY = settings.getString(KEY, "");
		if (CLIENT_API_ID > 0) {
			clientIdEdit.setText(CLIENT_API_ID.toString());
		}
		if (CLIENT_API_KEY.length() > 0) {
			clientApiKeyEdit.setText(CLIENT_API_KEY);
		}
		imageShowBtn = (Button) findViewById(R.id.btn_show_result);
	}

	@Override
	protected void onStop() {
		getValuesFromEdit();
		savePrefs();
		super.onStop();
	}

	public void infoClickHandler(View v) {
		Intent intent = new Intent(this, InfoActivity.class);
		startActivity(intent);
	}

	private void getValuesFromEdit() {
		// get values from edti text fields
		String clientApiIdString = clientIdEdit.getText().toString();
		Integer clientApiId = 0;
		if (clientApiIdString.length() > 0) {
			try {
				clientApiId = Integer.parseInt(clientApiIdString);
				CLIENT_API_ID = clientApiId;
			} catch (Exception e) {
				Toast.makeText(getApplicationContext(),
						"ID should be a correct number!", Toast.LENGTH_LONG)
						.show();
			}
		}
		CLIENT_API_KEY = clientApiKeyEdit.getText().toString();
	}

	public void showResult(View v) {
		if (response == null || response.getStatus() != 0) {
			return;
		}
		Intent intent = new Intent(this, ImageShowActivity.class);
		intent.putExtra("data", response);
		intent.putExtra("bitmap", pictureData);
		startActivity(intent);
	}

	public void makePhotoClick(View v) {
		getValuesFromEdit();

		if (CLIENT_API_KEY != null && CLIENT_API_KEY.length() > 0
				&& CLIENT_API_ID != null && CLIENT_API_ID > 0) {
			savePrefs();

			// Intent to take a photo
			Intent takePictureIntent = new Intent(
					MediaStore.ACTION_IMAGE_CAPTURE);
			takePictureIntent.putExtra(MediaStore.EXTRA_FULL_SCREEN, true);
			takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, true);
			takePictureIntent.putExtra(MediaStore.EXTRA_SHOW_ACTION_ICONS,
					false);
			startActivityForResult(takePictureIntent, 1234);
		} else {
			Toast.makeText(getApplicationContext(),
					"Fill in Your Client Id and API Key", Toast.LENGTH_SHORT)
					.show();
		}
	}

	private void savePrefs() {
		// Save prefs
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putInt(ID, CLIENT_API_ID);
		editor.putString(KEY, CLIENT_API_KEY);
		// Commit the edits!
		editor.commit();
	}

	// handler that receives response from api
	@SuppressLint("HandlerLeak")
	private Handler itraffApiHandler = new Handler() {
		// callback from api
		@Override
		public void handleMessage(Message msg) {
			dismissWaitDialog();
			Bundle data = msg.getData();
			if (data != null) {
				response = (APIResponse) data
						.getSerializable(ItraffApi.RESPONSE);
				// status ok
				if (response.getStatus() == 0) {
					String text = "Status: 0\tObjects: [";
					for (APIObject obj : response.getObjects()) {
						text += obj.getName() + ", ";
					}
					text = text.substring(0, text.length() - 2) + "]";

					responseView.setText(text);
					MainActivity.this.imageShowBtn.setVisibility(View.VISIBLE);
					// application error (for example timeout)
				} else if (response.getStatus() == -1) {
					responseView.setText(getResources().getString(
							R.string.app_error));
					MainActivity.this.imageShowBtn
							.setVisibility(View.INVISIBLE);
					// error from api
				} else {
					responseView.setText(getResources().getString(
							R.string.error)
							+ response.getMessage());
					MainActivity.this.imageShowBtn
							.setVisibility(View.INVISIBLE);
				}
			}
		}
	};

	private byte[] pictureData;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		log("onActivityResult reqcode, resultcode: " + requestCode + "  "
				+ resultCode);
		if (resultCode == Activity.RESULT_OK) {
			log("Activity.RESULT_OK");
			if (data != null) {
				log("data != null");
				Bundle bundle = data.getExtras();
				if (bundle != null) {
					log("bundle != null");

					// byte[] pictureData = bundle.getByteArray("pictureData");
					Bitmap image = (Bitmap) bundle.get("data");
					if (image != null) {
						log("image != null");

						// chceck internet connection
						if (ItraffApi.isOnline(getApplicationContext())) {
							showWaitDialog();
							SharedPreferences prefs = PreferenceManager
									.getDefaultSharedPreferences(getBaseContext());
							// send photo
							ItraffApi api = new ItraffApi(CLIENT_API_ID,
									CLIENT_API_KEY, TAG, true);
							Log.v("KEY", CLIENT_API_ID.toString());
							if (prefs.getString("mode", "single").equals(
									"multi")) {
								api.setMode(ItraffApi.MODE_MULTI);
							} else {
								api.setMode(ItraffApi.MODE_SINGLE);
							}

							ByteArrayOutputStream stream = new ByteArrayOutputStream();
							image.compress(Bitmap.CompressFormat.JPEG, 100,
									stream);
							pictureData = stream.toByteArray();
							api.sendPhoto(pictureData, itraffApiHandler,
									prefs.getBoolean("allResults", true));
						} else {
							// show message: no internet connection
							// available.

							Toast.makeText(
									getApplicationContext(),
									getResources().getString(
											R.string.not_connected),
									Toast.LENGTH_LONG).show();
						}
					}
				}
			}
		} else if (resultCode == RESULT_BMP_DAMAGED) {
			log("RESULT_BMP_DAMAGEDl");
		}
	}

	private void showWaitDialog() {
		if (waitDialog != null) {
			if (!waitDialog.isShowing()) {
				waitDialog.show();
			}
		} else {
			waitDialog = new ProgressDialog(this);
			waitDialog.setMessage(getResources().getString(
					R.string.wait_message));
			waitDialog.show();
		}
	}

	private void dismissWaitDialog() {
		try {
			if (waitDialog != null && waitDialog.isShowing()) {
				waitDialog.dismiss();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void log(String msg) {
		if (debug) {
			Log.v(TAG, msg);
		}
	}

	public boolean onKeyDown(int keycode, KeyEvent event) {
		if (keycode == KeyEvent.KEYCODE_MENU) {
			Intent intent = new Intent(this, Preferences.class);
			startActivity(intent);
			return true;
		}
		return super.onKeyDown(keycode, event);
	}

}