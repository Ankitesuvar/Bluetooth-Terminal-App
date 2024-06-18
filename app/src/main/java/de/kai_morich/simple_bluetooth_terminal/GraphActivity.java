package de.kai_morich.simple_bluetooth_terminal;

import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;

public class GraphActivity extends AppCompatActivity {

    private LineChart mChart;
    private static final int MAX_ENTRIES = 20; // Maximum number of data points to display


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_graph);

        mChart = (LineChart) findViewById(R.id.chart1);

        ArrayList<String> receivedData = getIntent().getStringArrayListExtra("splitData");
        if (receivedData != null) {
            setData(receivedData);
        } else {
            setData(new ArrayList<>()); // or some default data
        }

        mChart.animateX(1000);
    }

    private void setData(ArrayList<String> dataList) {
        ArrayList<Entry> yVal1 = new ArrayList<>();
        ArrayList<Entry> yVal2 = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            try {
                float val = Float.parseFloat(dataList.get(i));
                if (i % 2 == 0) {
                    yVal1.add(new Entry(i / 2, val));
                } else {
                    yVal2.add(new Entry(i / 2, val));
                }
            } catch (NumberFormatException e) {
                // Skip this value if it can't be parsed as a float
            }
        }

        // Trim the data lists to keep only the latest MAX_ENTRIES points
        while (yVal1.size() > MAX_ENTRIES) {
            yVal1.remove(0);
        }
        while (yVal2.size() > MAX_ENTRIES) {
            yVal2.remove(0);
        }



        LineDataSet set1, set2;

        set1 = new LineDataSet(yVal1, "Weight 1");
        set1.setColor(Color.RED);
        set1.setDrawCircles(true);
        set1.setLineWidth(2f);

        set2 = new LineDataSet(yVal2, "Weight 2");
        set2.setColor(Color.BLUE);
        set2.setDrawCircles(true);
        set2.setLineWidth(2f);

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(set1);
        dataSets.add(set2);

        LineData data = new LineData(set1, set2);
        mChart.setData(data);
        mChart.invalidate(); // refresh
        mChart.moveViewToX(yVal1.size() - MAX_ENTRIES); // Shift the graph to the right
    }
}
