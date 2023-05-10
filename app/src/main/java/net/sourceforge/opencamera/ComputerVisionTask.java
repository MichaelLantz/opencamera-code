package net.sourceforge.opencamera;

// Import required libraries
import java.io.File;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.gson.*;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;

import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.ActivityCompat;


// Function to upload image to Hugging Face dataset
public class ComputerVisionTask extends Thread {
    private static final String TAG = "ComputerVisionTask";
    private Context context;
    private File imageFile;
    private String datasetName;
    private String userName;
    private String hfToken;
    private String url;

    private static final String HF_BASE_URL = "https://huggingface.co";
    private static final String HF_UPLOAD_API_PATH = "/api/{type}/{repo_id}/upload/{revision}/{path_in_repo}";
    private static final String HF_API_DATASET_PATH = "/api/{type}/{repo_id}";//{revision}";
    private static final String MDB_API_PATH = "/api/sql/query";

    public ComputerVisionTask(Context context, File imageFile, String datasetName, String userName, String hfToken) {
        this.context = context;
        this.imageFile = imageFile;
        this.datasetName = datasetName;
        this.userName = userName;
        this.hfToken = hfToken;
    }

    protected void setHFToken(String hfToken) {
        this.hfToken = hfToken;
    }

    protected String uploadImage(Void... params) {
        OkHttpClient client = new OkHttpClient();
        url = "";
        try {
            RequestBody requestBody = RequestBody.create(imageFile, MediaType.parse("image/jpeg"));

            String type = "datasets";
            String revision = "main";
            String pathInRepo = imageFile.getName();

            Request request = new Request.Builder()
                    .url(HF_BASE_URL + HF_UPLOAD_API_PATH.replace("{type}", type)
                            .replace("{repo_id}",  userName + "/" + datasetName)
                            .replace("{revision}", revision)
                            .replace("{path_in_repo}", pathInRepo))
                    .addHeader("Authorization", "Bearer " + hfToken)
                    //.addHeader("Content-Type", "image/jpeg")
                    .post(requestBody)
                    .build();
            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

            // extract the value from the first result
            url = jsonObject.get("url").getAsString();
            //String[] lines = responseBody.split("\n");
            //url = lines[0];
        }
        catch (Exception e) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "Error Uploading: " +  e.getMessage());
                }
            }

        return url;
    }

    protected void updateImageFile(Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            imageFile = new File(cursor.getString(column_index));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected String imageTask(String mindsDBServer, String cookie) {
        OkHttpClient client = new OkHttpClient();
        String result = "Nothing";
        int dataset_length = 0;

        try {
            RequestBody requestBody = RequestBody.create(imageFile, MediaType.parse("image/jpeg"));

            String type = "datasets";
            String pathInRepo = imageFile.getName();

            Request request = new Request.Builder()
                    .url(HF_BASE_URL + HF_API_DATASET_PATH.replace("{type}", type)
                            .replace("{repo_id}",  userName + "/" + datasetName)
                            .replace("{path_in_repo}", pathInRepo))
                    .addHeader("Authorization", "Bearer " + hfToken)
                    .build();
            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
            // get the first element in the "data" array
            JsonArray dataArray = jsonObject.getAsJsonArray("siblings");

            // extract the value from the first result
            dataset_length = dataArray.size() - 1;

            url = "https://datasets-server.huggingface.co/assets/{repo_id}/--/{userName}--{datasetName}/train/{dataset_length}/image/image.jpg";
            //url = "https://datasets-server.huggingface.co/assets/{repo_id}/--/{userName}--{datasetName}/train/27/image/image.jpg";
            url = url.replace("{repo_id}",  userName + "/" + datasetName)
                    .replace("{userName}", userName)
                    .replace("{datasetName}", datasetName)
                    .replace("{dataset_length}", Integer.toString(dataset_length));

            TimeUnit.SECONDS.sleep(30);
        }
        catch (Exception e) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "Error Getting Dataset: " +  e.getMessage());
            }
        }


        try {
            client = new OkHttpClient();
            String queryValue = "SELECT detection " +
                    "FROM mindsdb.ppe_detection " +
                    "WHERE image = \\\"" + url + "\\\" LIMIT 1;";
            String queryJson = "{\"query\":\"" + queryValue + "\"}";

            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), queryJson);

            Request request = new Request.Builder()
                    .url(mindsDBServer + MDB_API_PATH)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();
            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

            // get the first element in the "data" array
            JsonArray dataArray = jsonObject.getAsJsonArray("data");

            // extract the value from the first result
            result = dataArray.get(0).getAsString();

        }
        catch (Exception e) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "Error Using Computer Vision: " +  e.getMessage());
            }
            e.printStackTrace();
        }
        return result;
    }
}
