package com.example.bluetoothdemo;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bluetoothdemo.connect.AcceptThread;
import com.example.bluetoothdemo.connect.ConnectThread;
import com.example.bluetoothdemo.connect.Constant;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_CODE = 0;
    private final List<BluetoothDevice> mDeviceList = new ArrayList<>();
    private List<BluetoothDevice> mBondedDeviceList = new ArrayList<>();

    private final BlueToothController mController = new BlueToothController();
    private final Handler mUIHandler = new MyHandler();

    private ListView mListView;
    private DeviceAdapter mAdapter;
    private Toast mToast;

    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;

    //防止蓝牙设备被重复搜索
    private HashMap<BluetoothDevice, Integer> deviceMap = new HashMap<BluetoothDevice,Integer>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();

        registerBluetoothReceiver();

        mController.turnOnBlueTooth(this,REQUEST_CODE);
    }

    private void registerBluetoothReceiver(){
        IntentFilter filter = new IntentFilter();
        //开始查找
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        //结束查找
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        //查找设备
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        //设备扫描模式改变
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        //绑定状态
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        registerReceiver(receiver, filter);
    }

    //注册广播监听搜索结果
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                //setProgressBarIndeterminateVisibility(true);
                //初始化数据列表
                mDeviceList.clear();
                mAdapter.notifyDataSetChanged();
            } else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                setProgressBarIndeterminateVisibility(false);
            }
            else if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //找到一个添加一个
                if(device!=null){
                    if(deviceMap.get(device) == null){
                        deviceMap.put(device,1);
                    }else{
                        deviceMap.put(device,deviceMap.get(device)+1);
                    }
                    //当device第一次被查找到的时候
                    if (deviceMap.get(device) == 1)
                        mDeviceList.add(device);
                    mAdapter.notifyDataSetChanged();
                }

            } else if(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {  //此处作用待细查
                int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 0);
                setProgressBarIndeterminateVisibility(scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
            } else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(remoteDevice == null) {
                    showToast("无设备");
                    return;
                }
                int status = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0);
                if(status == BluetoothDevice.BOND_BONDED) {
                    showToast("已绑定" + remoteDevice.getName());
                } else if(status == BluetoothDevice.BOND_BONDING) {
                    showToast("正在绑定" + remoteDevice.getName());
                } else if(status == BluetoothDevice.BOND_NONE) {
                    showToast("未绑定" + remoteDevice.getName());
                }
            }
        }
    };

    //初始化用户界面
    private void initUI() {
        mListView = findViewById(R.id.device_list);
        mAdapter = new DeviceAdapter(mDeviceList, this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(bondDeviceClick);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main,menu);
        return true;
    }

    /**
     * 导航栏菜单
     * @param item MenuItem
     * @return boolean
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.enable_visibility) {
            mController.enableVisibily(this);
        }
        //查找设备
        else if (id == R.id.find_device) {
            mAdapter.refresh(mDeviceList);
            mController.findDevice(this);
            mListView.setOnItemClickListener(bondDeviceClick);
        }
        //查看已绑定设备
        else if (id == R.id.bonded_device) {
            mBondedDeviceList = mController.getBondedDeviceList();
            mAdapter.refresh(mBondedDeviceList);
            mListView.setOnItemClickListener(bondedDeviceClick);
        }
        //开始监听
        else if( id == R.id.listening) {
            if( mAcceptThread != null) {
                mAcceptThread.cancel();
            }
            mAcceptThread = new AcceptThread(mController.getAdapter(), mUIHandler);
            mAcceptThread.start();
        }
        else if( id == R.id.stop_listening) {
            if( mAcceptThread != null) {
                mAcceptThread.cancel();
            }
        }
//        else if( id == R.id.disconnect) {
//            if( mConnectThread != null) {
//                mConnectThread.cancel();
//            }
//        }
        else if( id == R.id.say_hello) {
            say("Hello\n");
        }
        else if( id == R.id.say_hi) {
            say("Hi\n");
        }

        return super.onOptionsItemSelected(item);
    }

    private void say(String word) {
        if (mAcceptThread != null) {
            mAcceptThread.sendData(word.getBytes(StandardCharsets.UTF_8));
        }

        else if( mConnectThread != null) {
            mConnectThread.sendData(word.getBytes(StandardCharsets.UTF_8));
        }

    }

    private final AdapterView.OnItemClickListener bondDeviceClick = new AdapterView.OnItemClickListener() {
        @TargetApi(Build.VERSION_CODES.KITKAT)
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            BluetoothDevice device = mDeviceList.get(i);
            device.createBond();
        }
    };
    private final AdapterView.OnItemClickListener bondedDeviceClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            BluetoothDevice device = mBondedDeviceList.get(i);
            if (mConnectThread != null) {
                mConnectThread.cancel();
            }
            mConnectThread = new ConnectThread(device, mController.getAdapter(), mUIHandler);
            mConnectThread.start();
        }
    };


    private class MyHandler extends Handler {
        public void handleMessage(Message message) {
            super.handleMessage(message);
            switch (message.what) {
                case Constant.MSG_GOT_DATA:
                    showToast("data:" + String.valueOf(message.obj));
                    break;
                case Constant.MSG_ERROR:
                    showToast("error:" + message.obj);
                    break;
                case Constant.MSG_CONNECTED_TO_SERVER:
                    showToast("连接到服务端");
                    break;
                case Constant.MSG_GOT_A_CLINET:
                    showToast("找到服务端");
                    break;
            }
        }
    }

    //设置toast的标准格式
    @SuppressLint("ShowToast")
    private void showToast(String text){
        if(mToast == null){
            mToast = Toast.makeText(this, text,Toast.LENGTH_SHORT);
        }
        else {
            mToast.setText(text);
        }
        mToast.show();
    }

    /**
     * 退出时注销广播、注销连接过程、注销等待连接的监听
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
        }
        if (mConnectThread != null) {
            mConnectThread.cancel();
        }

        unregisterReceiver(receiver);
    }
}
