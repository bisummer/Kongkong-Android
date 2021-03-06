package cn.leanvision.normalkongkong;

/********************************
 * Created by lvshicheng on 15/11/19.
 * description
 ********************************/
public class Constants {
  /**
   * 网络请求缓存开关
   */
  public static final boolean CACHE_ENABLE   = true;
  /**
   * 网络请求缓存时长 （unit S）
   */
  public static final int     CACHE_TIME     = 5 * 60;
  /**
   * 测试服务器地址
   * http://ss1.chakonger.net.cn
   * 正式服务器，如需连接正式服务器请联系【精益开发】申请分配APP_ID，否则部分接口可能无法正常使用
   * http://ss2.chakonger.net.cn
   */
  public static final String  SERVER_ADDRESS = "http://ss1.chakonger.net.cn";
  /**
   * 替换成本公司分配到的APP_ID
   */
  public static final String  APP_ID         = "demo";

  /**
   * 插座连接服务器地址：固定的，不需要修改
   */
  public static final String BIND_ADDRESS = "ss2.chakonger.net.cn";
  public static final String BIND_PORT    = "80";

  public static final String ERROR_CODE_SUCCEED   = "0";
  /**
   * 设备状态
   */
  public static final String DEV_TYPE_OFFLINE     = "A002";
  public static final String DEV_TYPE_ONLINE      = "A003";
  public static final String DEV_TYPE_WORK        = "A004";
  /**
   * 以下是绑定的组合方式：默认只打开乐鑫绑定
   */
  public static final int    ONLY_LX              = 1;
  public static final int    ONLY_XAX             = 2;
  public static final int    MIX                  = 3;
  public static       int    BOUND_TYPE           = ONLY_LX;
  /******************
   * 所有请求后缀 - START
   ******************/
  /**
   * 获取通知地址
   */
  public static final String SUF_GET_PUSH_ADDRESS = "web/getpushaddress";
  /**
   * 查询推送消息
   */
  public static final String SUF_GET_PUSH_EVENT   = "web/getpushevent";
  /**
   * 发起绑定
   */
  public static final String SUF_POST_BIND        = "web/devicebind";
  /**
   * 控制面板以及红外支持能力查询
   */
  public static final String SUF_INFRA_QUERY      = "web/infratypeability";
  /**
   * 删除设备
   */
  public static final String SUF_DEVICE_REMOVE    = "web/deviceremove";
  /**
   * 单设备查询
   */
  public static final String SUF_DEVICE_QUERY     = "web/deviceqry";
  /**
   * 控制设备
   */
  public static final String SUF_DEVICE_CONTROL   = "web/action?actionID=%s&inst=%s&token=%s&infraTypeID=%s";

  /******************
   * 所有请求后缀 - END
   ******************/

  /**
   * 闹钟定时广播
   */
  public static final String LV_ACTION_REPEATE = "cn.leanvision.repeate";

  /**
   * 设备状态变更推送
   */
  public static final String PUSH_STATUS              = "N0A0";
  public static final String BROADCAST_STATUS         = "cn.leanvision.normalkongkong.status";
  /**
   * 设备被他人绑定推送
   */
  public static final String PUSH_BOUNDED             = "N2A0";
  public static final String BROADCAST_BOUNDED        = "cn.leanvision.normalkongkong.bounded";
  /**
   * 同步红外推送
   */
  public static final String PUSH_INFRA_SYNC          = "N4A0";
  public static final String BROADCAST_INFRA_SYNC     = "cn.leanvision.normalkongkong.infrasync";
  /**
   * 设备控制结果推送
   */
  public static final String PUSH_CONTROL_RESULT      = "N5A0";
  public static final String BROADCAST_CONTROL_RESULT = "cn.leanvision.normalkongkong.controlresult";
  /**
   * 新增红外推送
   */
  public static final String PUSH_NEW_INFRA_TYPE      = "N1A1";
  public static final String BROADCAST_INFRA_TYPE     = "cn.leanvision.normalkongkong.infratype";
  /**
   * 新增设备推送
   */
  public static final String PUSH_BIND_SUCCEED        = "N6A0";
  public static final String BROADCAST_BIND_SUCCEED   = "cn.leanvision.normalkongkong.bindsucceed";

}
