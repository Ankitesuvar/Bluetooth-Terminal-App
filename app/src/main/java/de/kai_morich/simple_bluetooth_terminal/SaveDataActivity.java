package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;


public class SaveDataActivity extends AppCompatActivity {

    private EditText editTextFileName;
    private File filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_data);

        editTextFileName = findViewById(R.id.editTextFileName);

        // Retrieve the splitData list from the Intent
        List<String> splitData = getIntent().getStringArrayListExtra("splitData");

        // Request storage permissions
        requestStoragePermissions(splitData);
    }

    public void buttonCreateExcelName(View view) {
        String fileName = editTextFileName.getText().toString().trim();
        if (fileName.isEmpty()) {
            editTextFileName.setError("Please enter a file name");
            return;
        }

        filePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName + ".csv");

        // Retrieve the splitData list from the Intent
        List<String> splitData = getIntent().getStringArrayListExtra("splitData");

        try {
            FileWriter writer = new FileWriter(filePath, true); // Append mode
            for (String data : splitData) {
                writer.append(data);
                writer.append(","); // Add a comma separator
            }
            writer.append("\n"); // Move to next line for the next entry
            writer.flush();
            writer.close();
            Toast.makeText(this, "CSV file created", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to create CSV file", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestStoragePermissions(List<String> splitData) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            saveDataToCSV(splitData);
        }
    }

    private void saveDataToCSV(List<String> splitData) {
        String fileName = editTextFileName.getText().toString().trim();
        if (fileName.isEmpty()) {
            editTextFileName.setError("Please enter a file name");
            return;
        }

        filePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName + ".csv");

        try {
            FileWriter writer = new FileWriter(filePath, true); // Append mode
            for (String data : splitData) {
                writer.append(data);
                writer.append(","); // Add a comma separator
            }
            writer.append("\n"); // Move to next line for the next entry
            writer.flush();
            writer.close();
            Toast.makeText(this, "CSV file created", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to create CSV file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            List<String> splitData = getIntent().getStringArrayListExtra("splitData");
            saveDataToCSV(splitData);
        } else {
            Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}

//public class SaveDataActivity extends AppCompatActivity {
//
//    private EditText editTextFileName;
//    private File filePath;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_save_data);
//
//        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
//                Manifest.permission.READ_EXTERNAL_STORAGE}, PackageManager.PERMISSION_GRANTED);
//
//        editTextFileName = findViewById(R.id.editTextFileName);
//
//        // Retrieve the received data from the Intent
//        String receivedData = getIntent().getStringExtra("receivedData");
//
//        // Request storage permissions
//        requestStoragePermissions(receivedData);
//    }
//
//    public void buttonCreateExcelName(View view) {
//        String fileName = editTextFileName.getText().toString().trim();
//        if (fileName.isEmpty()) {
//            editTextFileName.setError("Please enter a file name");
//            return;
//        }
//
//        filePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName + ".csv");
//
//        // Retrieve the received data from the Intent
//        String receivedData = getIntent().getStringExtra("receivedData");
//
//        try {
//            FileWriter writer = new FileWriter(filePath, true); // Append mode
//            writer.append(receivedData);
//            writer.append("\n"); // Move to next line for the next entry
//            writer.flush();
//            writer.close();
//            Toast.makeText(this, "CSV file created", Toast.LENGTH_SHORT).show();
//        } catch (IOException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Failed to create CSV file", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private void requestStoragePermissions(String receivedData) {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
//        } else {
//            saveDataToCSV(receivedData);
//        }
//    }
//
//    private void saveDataToCSV(String data) {
//        String fileName = editTextFileName.getText().toString().trim();
//        if (fileName.isEmpty()) {
//            editTextFileName.setError("Please enter a file name");
//            return;
//        }
//
//        filePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName + ".csv");
//
//        try {
//            FileWriter writer = new FileWriter(filePath, true); // Append mode
//            writer.append(data);
//            writer.append("\n"); // Move to next line for the next entry
//            writer.flush();
//            writer.close();
//            Toast.makeText(this, "CSV file created", Toast.LENGTH_SHORT).show();
//        } catch (IOException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Failed to create CSV file", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            String receivedData = getIntent().getStringExtra("receivedData");
//            saveDataToCSV(receivedData);
//        } else {
//            Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
//        }
//    }
//}
