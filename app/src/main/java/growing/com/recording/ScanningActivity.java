package growing.com.recording;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import growing.com.recording.utils.CommonUtil;
import growing.com.recording.zxing.activity.CaptureActivity;

/**
 * File: ScanningActivity.java
 * Author: ejiang
 * Version: V100R001C01
 * Create: 2017-06-14 17:31
 */

public class ScanningActivity extends AppCompatActivity implements OnClickListener {

    private Button mButton;
    private EditText mEditText;
    //打开扫描界面请求码
    private int REQUEST_CODE = 0x01;
    //扫描成功返回码
    private int RESULT_OK = 0xA1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanning);
        mButton = (Button) findViewById(R.id.button1);
        mButton.setOnClickListener(this);
        mEditText = (EditText) findViewById(R.id.edittext);

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
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //扫描结果回调
        if (resultCode == RESULT_OK) { //RESULT_OK = -1
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString("qr_scan_result");
            //将扫描出的信息显示出来
            mEditText.setText(scanResult);
        }
    }
}
