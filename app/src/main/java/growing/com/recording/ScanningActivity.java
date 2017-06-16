package growing.com.recording;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import growing.com.recording.utils.CommonUtil;
import growing.com.recording.zxing.activity.CaptureActivity;

import static growing.com.recording.BaseApplication.getAppData;
import static growing.com.recording.service.ForegroundService.getPcScoketServer;

/**
 * File: ScanningActivity.java
 * Author: ejiang
 * Version: V100R001C01
 * Create: 2017-06-14 17:31
 */

public class ScanningActivity extends AppCompatActivity implements OnClickListener {

    private Button mButton, mButton2;
    private TextView mEditText;
    //打开扫描界面请求码
    private int REQUEST_CODE = 0x01;
    //扫描成功返回码
    private int RESULT_OK = 0xA1;
    private static final String TAG = ScanningActivity.class.getCanonicalName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanning);
        mButton = (Button) findViewById(R.id.button1);
        mButton.setOnClickListener(this);
        mEditText = (TextView) findViewById(R.id.textview);
        mButton2 = (Button) findViewById(R.id.button2);
        mButton2.setOnClickListener(this);
        mButton2.setEnabled(false);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button1:
                //打开二维码扫描界面
                if (CommonUtil.isCameraCanUse()) {
                    Intent intent = new Intent(this, CaptureActivity.class);
                    startActivityForResult(intent, REQUEST_CODE);
                } else {
                    Toast.makeText(this, "请打开此应用的摄像头权限！", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.button2:
                if (mEditText.getText() != null && mEditText.getText().length() > 0) {
                    String str = mEditText.getText().toString();
                    int i = str.indexOf(":");
                    if (i != -1) {
                        String pcIp = str.substring(0, i);
                        String pcPcot = str.substring(i + 1, str.length());
                        Log.i(TAG, "onClick: " + pcIp);
                        Log.i(TAG, "onClick: " + pcPcot);
                        getAppData().setStrSocketUrl(pcIp);
                        getAppData().setStrSocketProt(Integer.parseInt(pcPcot));
                        if (getPcScoketServer() != null) {
                            getPcScoketServer().start();
                            startActivity(new Intent(this, MainActivity.class));
                        } else {
                            Toast.makeText(this, "socket启动失败", Toast.LENGTH_LONG).show();
                        }

                    } else {
                        Toast.makeText(this, "数据解析失败", Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
//        getAppData().setStreamRunning(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        getAppData().setStreamRunning(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //扫描结果回调
        if (resultCode == RESULT_OK) { //RESULT_OK = -1
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString("qr_scan_result");
            //将扫描出的信息显示出来
            mEditText.setText(scanResult);
            mButton2.setEnabled(true);
        }
    }
}
