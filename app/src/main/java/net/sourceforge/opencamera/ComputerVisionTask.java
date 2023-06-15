package net.sourceforge.opencamera;

// Import required libraries
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.gson.*;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import android.provider.MediaStore;
import android.util.Log;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.models.InputFile;
import io.appwrite.services.Storage;


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
        url = "";
        String endpoint = "https://cloud.appwrite.io/v1";
        String project = "..."; // Your Appwrite Project ID goes here
        String bucketId = "..."; // Your Appwrite Bucket ID goes here
        String mindsDBServer = "mindsDBServerURL";//"http://192.168.1.207:47334"; // Your MindsDB server URL goes here
        AtomicReference<String> imageTaskResult = new AtomicReference<>("Not Nothing");
        String imageTaskResultComparison = "Not Nothing";

        try {
            String pathInRepo = imageFile.getName();

            Client client = new Client(context)
                    .setEndpoint(endpoint) // Your API Endpoint
                    .setProject(project); // Your project ID

            Storage storage = new Storage(client);

            storage.createFile(
                    bucketId, // Your bucket Id
                    pathInRepo,
                    InputFile.Companion.fromFile(imageFile),
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            return;
                        }
                        url = endpoint + "/storage/buckets/"+ bucketId + "/files/"+ pathInRepo +"/view?project="+ project + "&mode=admin";
                        imageTaskResult.set(imageTask(mindsDBServer, null));

                        Log.d(TAG,"Appwrite " + result.toString());
                    })
            );

        }
        catch (Exception e) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "Error Uploading: " +  e.getMessage());
                };
            }
        while (imageTaskResult.get().equals(imageTaskResultComparison)){

        }
        Log.d(TAG,"Appwrite " + "Image Uploaded");
        return imageTaskResult.toString();
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
