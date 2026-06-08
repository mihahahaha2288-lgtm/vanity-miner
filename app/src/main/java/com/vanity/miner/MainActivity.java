package com.vanity.miner;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.*;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Color;
import android.text.InputType;
import android.widget.LinearLayout.LayoutParams;

public class MainActivity extends Activity {
    static { System.loadLibrary("vanity_miner"); }
    
    private native void nativeStartMining(int minRepeat, int threads, boolean useGPU);
    private native void nativeStopMining();
    private native long nativeGetTotal();
    private native long nativeGetFound();
    private native boolean nativeHasResult();
    private native String nativeGetResult();
    
    private TextView speedText, totalText, foundText, resultText;
    private EditText minRepeatInput;
    private RadioButton cpuRadio, gpuRadio;
    private SeekBar cpuSeekBar, gpuSeekBar;
    private TextView cpuValueText, gpuValueText;
    private Button startBtn, stopBtn;
    
    private Handler handler = new Handler();
    private long lastTotal = 0;
    private long lastTime = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);
        
        TextView title = new TextView(this);
        title.setText("🔑 TON Vanity Scanner");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#00FF88"));
        layout.addView(title);
        
        addSpace(layout, 10);
        
        speedText = new TextView(this);
        speedText.setText("⚡ Скорость: 0/сек");
        speedText.setTextSize(16);
        layout.addView(speedText);
        
        totalText = new TextView(this);
        totalText.setText("📊 Проверено: 0");
        layout.addView(totalText);
        
        foundText = new TextView(this);
        foundText.setText("💎 Найдено: 0");
        layout.addView(foundText);
        
        resultText = new TextView(this);
        resultText.setText("");
        resultText.setTextSize(12);
        resultText.setTextColor(Color.parseColor("#FFAA00"));
        layout.addView(resultText);
        
        addSpace(layout, 10);
        
        TextView filterLabel = new TextView(this);
        filterLabel.setText("Мин. повторений:");
        filterLabel.setTextColor(Color.parseColor("#FFAA00"));
        layout.addView(filterLabel);
        
        minRepeatInput = new EditText(this);
        minRepeatInput.setText("5");
        minRepeatInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(200, LayoutParams.WRAP_CONTENT);
        minRepeatInput.setLayoutParams(params);
        layout.addView(minRepeatInput);
        
        addSpace(layout, 10);
        
        TextView modeLabel = new TextView(this);
        modeLabel.setText("⚙️ Режим:");
        modeLabel.setTextColor(Color.parseColor("#FFAA00"));
        layout.addView(modeLabel);
        
        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(LinearLayout.HORIZONTAL);
        cpuRadio = new RadioButton(this);
        cpuRadio.setText("💪 CPU");
        cpuRadio.setChecked(true);
        modeGroup.addView(cpuRadio);
        gpuRadio = new RadioButton(this);
        gpuRadio.setText("🎮 GPU");
        modeGroup.addView(gpuRadio);
        layout.addView(modeGroup);
        
        TextView cpuLabel = new TextView(this);
        cpuLabel.setText("Потоки CPU:");
        layout.addView(cpuLabel);
        
        cpuSeekBar = new SeekBar(this);
        cpuSeekBar.setMax(16);
        cpuSeekBar.setProgress(4);
        layout.addView(cpuSeekBar);
        
        cpuValueText = new TextView(this);
        cpuValueText.setText("4 потока");
        cpuSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { cpuValueText.setText(p + " потока"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(cpuValueText);
        
        TextView gpuLabel = new TextView(this);
        gpuLabel.setText("Нагрузка GPU:");
        layout.addView(gpuLabel);
        
        gpuSeekBar = new SeekBar(this);
        gpuSeekBar.setMax(100);
        gpuSeekBar.setProgress(80);
        layout.addView(gpuSeekBar);
        
        gpuValueText = new TextView(this);
        gpuValueText.setText("80%");
        gpuSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { gpuValueText.setText(p + "%"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(gpuValueText);
        
        addSpace(layout, 20);
        
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        
        startBtn = new Button(this);
        startBtn.setText("▶ СТАРТ");
        startBtn.setBackgroundColor(Color.parseColor("#00AA44"));
        startBtn.setOnClickListener(v -> startMining());
        btnRow.addView(startBtn);
        
        stopBtn = new Button(this);
        stopBtn.setText("⏹ СТОП");
        stopBtn.setBackgroundColor(Color.parseColor("#CC3333"));
        stopBtn.setEnabled(false);
        stopBtn.setOnClickListener(v -> stopMining());
        btnRow.addView(stopBtn);
        
        layout.addView(btnRow);
        
        scroll.addView(layout);
        setContentView(scroll);
        
        handler.postDelayed(updateStats, 1000);
    }
    
    private Runnable updateStats = new Runnable() {
        public void run() {
            if (lastTime == 0) { lastTime = System.currentTimeMillis(); lastTotal = 0; }
            
            long now = System.currentTimeMillis();
            long total = nativeGetTotal();
            long found = nativeGetFound();
            double elapsed = (now - lastTime) / 1000.0;
            
            if (elapsed > 0) {
                int speed = (int)((total - lastTotal) / elapsed);
                speedText.setText("⚡ Скорость: " + speed + "/сек");
            }
            
            totalText.setText("📊 Проверено: " + total);
            foundText.setText("💎 Найдено: " + found);
            
            lastTime = now;
            lastTotal = total;
            
            if (nativeHasResult()) {
                String result = nativeGetResult();
                String[] parts = result.split("\\|");
                if (parts.length == 2) {
                    resultText.setText("✅ Адрес: " + parts[0] + "\n🔑 Ключ: " + parts[1]);
                }
            }
            
            handler.postDelayed(this, 1000);
        }
    };
    
    private void addSpace(LinearLayout layout, int height) {
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        layout.addView(space);
    }
    
    private void startMining() {
        int minRepeat = Integer.parseInt(minRepeatInput.getText().toString());
        int threads = cpuSeekBar.getProgress();
        boolean useGPU = gpuRadio.isChecked();
        
        lastTime = 0;
        lastTotal = 0;
        resultText.setText("");
        
        nativeStartMining(minRepeat, threads, useGPU);
        
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
    }
    
    private void stopMining() {
        nativeStopMining();
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        speedText.setText("⚡ Скорость: 0/сек");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        nativeStopMining();
        handler.removeCallbacks(updateStats);
    }
}
