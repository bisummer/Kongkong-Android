package cn.leanvision.normalkongkong.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.mime.VolleyHelper;
import com.espressif.iot.esptouch.EspWifiAdminSimple;
import com.espressif.iot.esptouch.task.EsptouchTaskParameter;
import com.espressif.iot.esptouch.task.IEsptouchTaskParameter;
import com.espressif.iot.esptouch.task.__EsptouchTask;
import com.espressif.iot.esptouch.udp.UDPSocketServer;
import com.espressif.iot.esptouch.util.EspNetUtil;
import com.nufront.nusmartconfig.ConfigWireless;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

import butterknife.Bind;
import cn.leanvision.common.util.LogUtil;
import cn.leanvision.common.util.StringUtil;
import cn.leanvision.normalkongkong.CommonUtil;
import cn.leanvision.normalkongkong.Constants;
import cn.leanvision.normalkongkong.R;
import cn.leanvision.normalkongkong.framework.LvIBaseHandler;
import cn.leanvision.normalkongkong.framework.activity.LvBaseActivity;
import cn.leanvision.normalkongkong.framework.request.JsonObjectRequest;
import cn.leanvision.normalkongkong.framework.sharepreferences.SharedPrefHelper;
import cn.leanvision.normalkongkong.widget.BoundProgressView;
import cn.leanvision.normalkongkong.widget.RippleBackground;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * @author lvshicheng
 * @date 2015年12月22日12:10:05
 * @description 绑定页面 - 绑定过程中不要离开该页面
 */
public class BoundActivity extends LvBaseActivity {

  public static final int UDP_PORT = 7788;

  public static final int UDP_PAIR_RETURN = 27;

  public static final int BOUND_SUCCEED = 28;
  public static final int BOUND_FAILED  = 30;

  //    public static final int BOUND_WAIT_DELAY = 29;
  public static final int SEND_REPEAT = 31;

  @Bind(R.id.bottom_bpv)
  BoundProgressView mBoundProgressView;
  @Bind(R.id.tv_desc)
  TextView          tv_desc;
  @Bind(R.id.content)
  RippleBackground  rippleBackground;

  private String           seed;
  private SharedPrefHelper sph;
  private String           wifiSsid;
  private String           wifiPwd;

  // 乐鑫
  private __EsptouchTask esptouchTask;
  // 新岸线
  private ConfigWireless configWireless;

  //  private ExecutorService  espExecutorService;
//  private EspTouchRunnable espTouchRunnable;
//
//  private ExecutorService     receiverExecutorService;
//  private UdpReceiverRunnable udpReceiverRunnable;
  private LvHandler lvHandler;

  // 暂时这么处理
  private boolean isSeedSendSucceed = false;
  private BroadcastReceiver pushReceiver;
  private int sendTimes = 0;

  private String     address;
  private int        port;
  private JSONObject jsonObject;

  private CompositeSubscription compositeSubscription = new CompositeSubscription();

  public static Intent createIntent(Context context, String wifiSsid) {
    Intent intent = new Intent(context, BoundActivity.class);
    intent.putExtra("SSID", wifiSsid);
    return intent;
  }

  @Override
  protected void setContentViewLv() {
    /** 保持屏幕常亮 */
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.activity_bound);

    sph = SharedPrefHelper.getInstance();
    seed = CommonUtil.getDeviceRandomNum();

    wifiSsid = getIntent().getStringExtra("SSID");
    wifiPwd = sph.getWifiPwd();
    lvHandler = new LvHandler(this);
  }

  @Override
  protected void initViewLv() {
    setupToolbar(R.string.title_activity_bound);

    rippleBackground = (RippleBackground) findViewById(R.id.content);
    rippleBackground.startRippleAnimation();

    mBoundProgressView = (BoundProgressView) findViewById(R.id.bottom_bpv);
    mBoundProgressView.setStep(0);

    tv_desc = (TextView) findViewById(R.id.tv_desc);
    tv_desc.setText(R.string.desc_one);
  }

  @Override
  protected void afterInitView() {
    httpPostBound();
  }

  @Override
  protected void onStart() {
    super.onStart();
    // seed 必须发送成功后才开始配对
    if (isSeedSendSucceed) {
      startUdpReceiver();
      startEsptouch();
    }
    registerPushReceiver();
  }

  @Override
  protected void onStop() {
    super.onStop();
    stopUdpReceiver();
    stopEsptouch(false);
    unregisterPushReceiver();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    if (compositeSubscription != null && !compositeSubscription.isUnsubscribed()) {
      compositeSubscription.unsubscribe();
    }
  }

  public void registerPushReceiver() {
    if (pushReceiver == null) {
      LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getApplicationContext());
      pushReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (Constants.BROADCAST_BIND_SUCCEED.equals(action)) {
            // 绑定成功
            mBoundProgressView.setStep(3);
            lvHandler.sendEmptyMessageDelayed(BOUND_SUCCEED, 200);
          }
        }
      };
      IntentFilter filter = new IntentFilter();
      filter.addAction(Constants.BROADCAST_BIND_SUCCEED);
      manager.registerReceiver(pushReceiver, filter);
    }
  }

  public void unregisterPushReceiver() {
    if (pushReceiver != null) {
      LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getApplicationContext());
      manager.unregisterReceiver(pushReceiver);
      pushReceiver = null;
    }
  }

  /*******************************
   * http request zone - START
   *******************************/
  private void httpPostBound() {
    String url = CommonUtil.formatUrl(Constants.SUF_POST_BIND);
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("sessionID", sph.getSessionID());
    jsonObject.put("seed", seed);
    jsonObject.put("appid", Constants.APP_ID);
    String body = jsonObject.toJSONString();
    Response.Listener<JSONObject> listener = new Response.Listener<JSONObject>() {
      @Override
      public void onResponse(JSONObject response) {

        String errcode = response.getString("errcode");
        if (Constants.ERROR_CODE_SUCCEED.equals(errcode)) {
          isSeedSendSucceed = true;
          startBound();
        } else {
          isSeedSendSucceed = false;
          // SEED 发送失败
          bindFailed(response.getString("errmsg"));
        }
      }
    };
    Response.ErrorListener errorListener = new Response.ErrorListener() {
      @Override
      public void onErrorResponse(VolleyError error) {
        error.printStackTrace();
      }
    };
    JsonObjectRequest request = new JsonObjectRequest(url, body, listener, errorListener);
    VolleyHelper.addRequest(this, request, requestTag);
  }

  /*******************************
   * http request zone - END
   *******************************/
  public void bindFailed(String msg) {
    if (!TextUtils.isEmpty(msg)) {
      Toast.makeText(this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }
    lvHandler.sendEmptyMessage(BOUND_FAILED);
  }

  /**
   * 开始绑定
   */
  public void startBound() {
    startEsptouch();
    startUdpReceiver();
  }

  /**
   * 开始发送绑定信息
   */
  private void startEsptouch() {
    EspWifiAdminSimple mWifiAdmin = new EspWifiAdminSimple(this);
    final String apBssid = mWifiAdmin.getWifiConnectedBssid();
    //开始绑定，传入wifiSSID(wifi名称)，wifiPWD(wifi密码)，apBssid(路由器mac地址)
    compositeSubscription.add(Observable.create(
        new Observable.OnSubscribe<Void>() {
          @Override
          public void call(Subscriber<? super Void> subscriber) {
            EspWifiAdminSimple esp = new EspWifiAdminSimple(getApplicationContext());
            String ssid = wifiSsid;
            String bssid = apBssid;
            String pwd = wifiPwd;

            IEsptouchTaskParameter parameter = new EsptouchTaskParameter();
            esptouchTask = new __EsptouchTask(ssid, bssid, pwd, getApplicationContext(), parameter, false);

            String localIpAddress = EspNetUtil.getLocalIpAddress(getApplicationContext());
            configWireless = new ConfigWireless(ssid, pwd, localIpAddress);
            while (!subscriber.isUnsubscribed()) {
              if (Constants.BOUND_TYPE == Constants.ONLY_LX && esptouchTask != null) {
                esptouchTask.execute();
              } else if (Constants.BOUND_TYPE == Constants.ONLY_XAX && configWireless != null) {
                configWireless.execute();
              } else if (Constants.BOUND_TYPE == Constants.MIX && esptouchTask != null && configWireless != null) {
                esptouchTask.execute();
                try {
                  Thread.sleep(3 * 1000);
                } catch (InterruptedException e) {
                  e.printStackTrace();
                }
                for (int i = 0; i < 3; i++) {
                  configWireless.execute();
                }
              }
            }
            subscriber.onNext(null);
          }
        })
        .subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Void>() {
          @Override
          public void call(Void o) {
            // 发送完毕
            if (jsonObject == null) {
              bindFailed(null);
            } else {
              mBoundProgressView.setStep(1);
            }
          }
        })
    );

//    if (this.isFinishing())
//      return;
//    EspWifiAdminSimple mWifiAdmin = new EspWifiAdminSimple(this);
//    String apBssid = mWifiAdmin.getWifiConnectedBssid();
//    espExecutorService = Executors.newSingleThreadExecutor();
//    //开始绑定，传入wifiSSID(wifi名称)，wifiPWD(wifi密码)，apBssid(路由器mac地址)
//    espTouchRunnable = new EspTouchRunnable(wifiSsid, apBssid, wifiPwd, this);
//    espExecutorService.execute(espTouchRunnable);
  }

  /**
   * 停止发送绑定信息
   */
  private void stopEsptouch(boolean b) {
    if (esptouchTask != null) {
      esptouchTask.interrupt();
      esptouchTask = null;
    }

    if (configWireless != null) {
      configWireless.interrupt();
      configWireless = null;
    }

//    if (espTouchRunnable != null)
//      espTouchRunnable.cancel(b);
//    if (espExecutorService != null) {
//      espExecutorService.shutdown();
//      espExecutorService = null;
//    }
  }

  private UDPSocketServer udpSocketServer;

  /**
   * 开始监听插座返回信息
   */
  public void startUdpReceiver() {
    compositeSubscription.add(Observable.create(
        new Observable.OnSubscribe<String>() {
          @Override
          public void call(Subscriber<? super String> subscriber) {
            udpSocketServer = new UDPSocketServer(7788, Integer.MAX_VALUE, getApplicationContext());
            byte[] bytes = udpSocketServer.receiveBytes();
            String date = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
//            subscriber.onNext("连接路由器返回 : " + date + "\r\n" + new String(bytes));
            subscriber.onNext(new String(bytes));
          }
        })
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Action1<String>() {
              @Override
              public void call(String result) {

                if (StringUtil.isNullOrEmpty(result)) {
                  //do nothing
                } else {
                  //返回结果
                  LogUtil.e("RESULT返回数据 : " + result);
                  JSONObject parseObject = JSONObject.parseObject(result);
                  String devSn = parseObject.getString("devSn");

                  address = udpSocketServer.getHostAddress();
                  port = udpSocketServer.getPort();

                  // 这里我拿到了连接WIFI后的返回信息进行服务器配置
                  // 将服务器信息通过UDP发送给插座
                  String serverAddress = Constants.BIND_ADDRESS;
                  String serverAddressPort = Constants.BIND_PORT;
                  jsonObject = new JSONObject();
                  jsonObject.put("domain", serverAddress);
                  jsonObject.put("port", serverAddressPort);
                  jsonObject.put("seed", seed);
                  jsonObject.put("devSn", devSn);
                  /**
                   * {"domain":"域名","port":"端口","seed":"5位随机数(当devSn已经是20位时可发可不发)"
                   * , "devSn":"设备号(应该与以前插座发出的完全一致)"}
                   * */
                  LogUtil.e("发送数据 : " + jsonObject.toString());
                  // 停止发送绑定信息
                  Message message = lvHandler.obtainMessage(BoundActivity.UDP_PAIR_RETURN);
                  message.obj = devSn;
                  lvHandler.sendMessage(message);
                }
              }
            })
    );

//    if (null == receiverExecutorService) {
//      receiverExecutorService = Executors.newSingleThreadExecutor();
//      udpReceiverRunnable = new UdpReceiverRunnable();
//      receiverExecutorService.execute(udpReceiverRunnable);
//    }
  }

  /**
   * 停止监听插座返回信息
   */
  public void stopUdpReceiver() {
    if (udpSocketServer != null) {
      udpSocketServer.interrupt();
      udpSocketServer = null;
    }

//    if (null != udpReceiverRunnable) {
//      udpReceiverRunnable.cancel();
//    }
//    if (null != receiverExecutorService) {
//      receiverExecutorService.shutdown();
//      receiverExecutorService = null;
//    }
  }


//  /**
//   * 绑定发送结束返回
//   */
//  private IEsptouchListener myListener = new IEsptouchListener() {
//    @Override
//    public void onEsptouchResultAdded(final IEsptouchResult result) {
//    }
//
//    @Override
//    public void sendComplete(boolean b) {
//      LogUtil.log(getClass(), "send Complete");
//      if (b) {
//        runOnUiThread(new Runnable() {
//          @Override
//          public void run() {
//            //END SEND
//            mBoundProgressView.setStep(1);
//          }
//        });
//      } else {
//        bindFailed();
//      }
//    }
//  };


//  /**
//   * 执行绑定任务
//   */
//  public class EspTouchRunnable implements Runnable {
//
//    private final String                       apSsid;
//    private final String                       apBssid;
//    private final String                       apPassword;
//    private       IEsptouchTask                mEsptouchTask;
//    private       WeakReference<BoundActivity> wActivity;
//
//    public EspTouchRunnable(String apSsid, String apBssid, String apPassword, BoundActivity mActvity) {
//      this.apSsid = apSsid;
//      this.apBssid = apBssid;
//      this.apPassword = apPassword;
//      wActivity = new WeakReference<>(mActvity);
//    }
//
//    @Override
//    public void run() {
//      BoundActivity activity = wActivity.get();
//      if (activity == null)
//        return;
//      boolean isSsidHidden = false;
//      mEsptouchTask = new EsptouchTask(apSsid, apBssid, apPassword,
//          isSsidHidden, activity);
//      mEsptouchTask.setEsptouchListener(activity.myListener);
//      try {
//        mEsptouchTask.executeForResults(1);
//      } catch (IOException e) {
//        //TODO 报文发送失败？？？
//        LogUtil.log(getClass(), " ------ 发送报文错误！！！");
//        e.printStackTrace();
//        //结束得了
//        cancel(false);
//      }
//    }
//
//    public void cancel(boolean b) {
//      if (mEsptouchTask != null) {
//        mEsptouchTask.setIsSucceed(b);
//        mEsptouchTask.interrupt();
//      }
//    }
//  }

//  /**
//   * 监听绑定过程中插座连接路由器后返回的报文
//   */
//  public class UdpReceiverRunnable implements Runnable {
//
//    private UDPSocketServer lvUdpServer;
//    private int count = 0;
//
//    public UdpReceiverRunnable() {
//
//    }
//
//    @Override
//    public void run() {
//      lvUdpServer = new UDPSocketServer(UDP_PORT, -1, getApplicationContext());
//      String result = lvUdpServer.receiveSpecLenBytes();
//
//      if (StringUtil.isNullOrEmpty(result)) {
//        //do nothing
//      } else {
//        //返回结果
//        LogUtil.e("RESULT返回数据 : " + result);
//        JSONObject parseObject = JSONObject.parseObject(result);
//        String devSn = parseObject.getString("devSn");
//
//        address = parseObject.getString("address");
//        port = parseObject.getIntValue("port");
//
//        // 这里我拿到了连接WIFI后的返回信息进行服务器配置
//        // 将服务器信息通过UDP发送给插座
//        String serverAddress = Constants.BIND_ADDRESS;
//        String serverAddressPort = Constants.BIND_PORT;
//        jsonObject = new JSONObject();
//        jsonObject.put("domain", serverAddress);
//        jsonObject.put("port", serverAddressPort);
//        jsonObject.put("seed", seed);
//        jsonObject.put("devSn", devSn);
//        /**
//         * {"domain":"域名","port":"端口","seed":"5位随机数(当devSn已经是20位时可发可不发)"
//         * , "devSn":"设备号(应该与以前插座发出的完全一致)"}
//         * */
//        LogUtil.e("发送数据 : " + jsonObject.toString());
////                sendServerInfo(address, port, jsonObject);
//        // 停止发送绑定信息
//        Message message = lvHandler.obtainMessage(BoundActivity.UDP_PAIR_RETURN);
//        message.obj = devSn;
//        lvHandler.sendMessage(message);
//      }
//    }
//
//    public void cancel() {
//      if (lvUdpServer != null) {
//        lvUdpServer.interrupt();
//        lvUdpServer = null;
//      }
//    }
//  }

  /**
   * 通过UDP发送服务器信息到插座
   */
  private void sendServerInfo() {
    Executors.newCachedThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        try {
          DatagramSocket datagramSocket = new DatagramSocket();
          byte[] data = jsonObject.toString().getBytes("UTF-8");
          DatagramPacket datagramPacket = new DatagramPacket(data, data.length, InetAddress.getByName(address), port);
          datagramSocket.send(datagramPacket);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
  }

  public static class LvHandler extends LvIBaseHandler<BoundActivity> {

    public LvHandler(BoundActivity boundActivity) {
      super(boundActivity);
    }

    @Override
    public void handleMessage(Message msg) {
      super.handleMessage(msg);
      if (!canGoNext())
        return;
      BoundActivity activity = getActivity();
      switch (msg.what) {
        case UDP_PAIR_RETURN:
          // 等待插座上线  N6A0
          activity.stopEsptouch(true);
          activity.stopUdpReceiver();
          // 开始发送服务器信息，每五秒钟一次，查询12次
          sendEmptyMessage(SEND_REPEAT);
          break;
        case BOUND_SUCCEED:
//                    this.removeMessages(BOUND_WAIT_DELAY);
          Intent intent = MainActivity.createIntent(activity, MainActivity.TYPE_BOUNDED);
          intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
          activity.startActivity(intent);
          break;
//                case BOUND_WAIT_DELAY:
        case BOUND_FAILED:
          activity.showSnackBar(R.string.bound_failed);
          activity.finish();
          break;
        case SEND_REPEAT:
          if (activity.sendTimes < 12) {
            activity.sendServerInfo();
            activity.sendTimes++;
            sendEmptyMessageDelayed(SEND_REPEAT, 5 * 1000);
          } else {
            sendEmptyMessage(BOUND_FAILED);
          }
          break;
      }
    }
  }
}
