package com.hz.example.api.express;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * 快递鸟物流轨迹即时查询接口
 *
 * @技术QQ群: 456320272
 * @see: http://www.kdniao.com/YundanChaxunAPI.aspx
 * @copyright: 深圳市快金数据技术服务有限公司
 *
 * DEMO中的电商ID与私钥仅限测试使用，正式环境请单独注册账号
 * 单日超过500单查询量，建议接入我方物流轨迹订阅推送接口
 *
 * ID和Key请到官网申请：http://www.kdniao.com/ServiceApply.aspx
 */
public class KdniaoTrackQueryAPI {

    private static final Logger logger = LoggerFactory.getLogger(KdniaoTrackQueryAPI.class);

    // DEMO
    public static void main(String[] args) {
        try {
            ExpressInfo expressInfo = KdniaoTrackQueryAPI.getOrderTracesByJson("YTO", "800338386116870005");

            if (expressInfo != null) {
                System.out.println("用户ID: ["+expressInfo.getBusinessId()+"]");
                System.out.println("订单编号: ["+expressInfo.getOrderCode()+"]");
                System.out.println("快递公司: ["+expressInfo.getShipperName()+"]");
                System.out.println("物流运单号: ["+expressInfo.getLogisticCode()+"]");
                System.out.println("成功与否: ["+expressInfo.getSuccess()+"]");
                System.out.println("失败原因: ["+expressInfo.getReason()+"]");
                System.out.println("物流状态: ["+expressInfo.getStateStr()+"]");

                expressInfo.getExpressTraceList().forEach(e -> {
                    System.out.println("--------------------------------");
                    System.out.println("时间: ["+e.getAcceptTime()+"]");
                    System.out.println("描述: ["+e.getAcceptStation()+"]");
                    System.out.println("备注: ["+e.getRemark()+"]");
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 电商ID
    private static final String EBusinessID = "请到快递鸟官网申请http://www.kdniao.com/ServiceApply.aspx";
    // 电商加密私钥，快递鸟提供，注意保管，不要泄漏
    private static final String AppKey = "请到快递鸟官网申请http://www.kdniao.com/ServiceApply.aspx";
    // 请求url
    private static final String ReqURL = "http://api.kdniao.cc/Ebusiness/EbusinessOrderHandle.aspx";

    /**
     * Json方式 查询订单物流轨迹
     *
     * @param expCode 物流公司代码, 快递公司编码可到官网查询：http://www.kdniao.com/api-track
     * @param expNo 物流单号
     * @return
     * @throws Exception
     */
    public static ExpressInfo getOrderTracesByJson(String expCode, String expNo) throws Exception{
        logger.info("expCode:{}, expNo:{}", expCode, expNo);

        if (StringUtils.isAnyBlank(expCode, expNo)) {
            logger.info("必填参数不能为空 expCode:{}, expNo:{}", expCode, expNo);
            return null;
        }

        String requestData= "{'OrderCode':'','ShipperCode':'" + expCode + "','LogisticCode':'" + expNo + "'}";

        Map<String, String> params = new HashMap<String, String>();
        params.put("RequestData", urlEncoder(requestData, "UTF-8"));
        params.put("EBusinessID", EBusinessID);
        params.put("RequestType", "1002");
        String dataSign = encrypt(requestData, AppKey, "UTF-8");
        params.put("DataSign", urlEncoder(dataSign, "UTF-8"));
        params.put("DataType", "2");

        String result = sendPost(ReqURL, params);
        logger.info("result:{}", result);

        //根据公司业务处理返回的信息......
        if (StringUtils.isBlank(result)) {
            logger.info("expCode:{}, expNo:{} 没有查询到快递信息。", expCode, expNo);
            return null;
        }

        JSONObject json = JSONObject.parseObject(result);
        if (json == null) {
            logger.info("expCode:{}, expNo:{} 没有查询到快递信息。", expCode, expNo);
            return null;
        }

        ExpressInfo expressInfo = new ExpressInfo();
        String eBusinessId = json.getString("EBusinessID");
        String orderCode = json.getString("OrderCode");
        String shipperCode = json.getString("ShipperCode");
        String logisticCode = json.getString("LogisticCode");
        Boolean success = json.getBoolean("Success");
        String reason = json.getString("Reason");
        Integer state = json.getInteger("State");
        String stateStr = "";
        if(state != null){
            switch (state) {
                case 2:
                    stateStr = "在途中";
                    break;
                case 3:
                    stateStr = "签收";
                    break;
                case 4:
                    stateStr = "问题件";
                    break;
            }
        }

        JSONArray array = json.getJSONArray("Traces");
        if(array != null){
            for (int i = array.size()-1; i >= 0; i--) {
                JSONObject j =  array.getJSONObject(i);
                if (j != null) {
                    String acceptTime = j.getString("AcceptTime"); //时间
                    String acceptStation = j.getString("AcceptStation"); //描述
                    String remark = j.getString("Remark"); //备注
                    ExpressTrace expressTrace = new ExpressTrace();
                    expressTrace.setAcceptTime(acceptTime);
                    expressTrace.setAcceptStation(acceptStation);
                    expressTrace.setRemark(remark);
                    expressInfo.addExpressTrace(expressTrace);
                }
            }
        }

        expressInfo.setBusinessId(eBusinessId);
        expressInfo.setOrderCode(orderCode);
        expressInfo.setShipperCode(shipperCode);
        expressInfo.setLogisticCode(logisticCode);
        expressInfo.setSuccess(success);
        expressInfo.setReason(reason);
        expressInfo.setState(state);
        expressInfo.setStateStr(stateStr);

        logger.info("return：{}", JSON.toJSONString(expressInfo));

        return expressInfo;
    }

    /**
     * XML方式 查询订单物流轨迹
     * @throws Exception
     */
    public static String getOrderTracesByXml() throws Exception{
        String requestData= "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"+
                "<Content>"+
                "<OrderCode></OrderCode>"+
                "<ShipperCode>SF</ShipperCode>"+
                "<LogisticCode>589707398027</LogisticCode>"+
                "</Content>";

        Map<String, String> params = new HashMap<String, String>();
        params.put("RequestData", urlEncoder(requestData, "UTF-8"));
        params.put("EBusinessID", EBusinessID);
        params.put("RequestType", "1002");
        String dataSign=encrypt(requestData, AppKey, "UTF-8");
        params.put("DataSign", urlEncoder(dataSign, "UTF-8"));
        params.put("DataType", "1");

        String result=sendPost(ReqURL, params);

        //根据公司业务处理返回的信息......

        return result;
    }

    /**
     * MD5加密
     * @param str 内容
     * @param charset 编码方式
     * @throws Exception
     */
    @SuppressWarnings("unused")
    private static String MD5(String str, String charset) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(str.getBytes(charset));
        byte[] result = md.digest();
        StringBuffer sb = new StringBuffer(32);
        for (int i = 0; i < result.length; i++) {
            int val = result[i] & 0xff;
            if (val <= 0xf) {
                sb.append("0");
            }
            sb.append(Integer.toHexString(val));
        }
        return sb.toString().toLowerCase();
    }

    /**
     * base64编码
     * @param str 内容
     * @param charset 编码方式
     * @throws UnsupportedEncodingException
     */
    private static String base64(String str, String charset) throws UnsupportedEncodingException{
        String encoded = base64Encode(str.getBytes(charset));
        return encoded;
    }

    @SuppressWarnings("unused")
    private static String urlEncoder(String str, String charset) throws UnsupportedEncodingException{
        String result = URLEncoder.encode(str, charset);
        return result;
    }

    /**
     * 电商Sign签名生成
     * @param content 内容
     * @param keyValue Appkey
     * @param charset 编码方式
     * @throws UnsupportedEncodingException ,Exception
     * @return DataSign签名
     */
    @SuppressWarnings("unused")
    private static String encrypt (String content, String keyValue, String charset) throws UnsupportedEncodingException, Exception {
        if (keyValue != null) {
            return base64(MD5(content + keyValue, charset), charset);
        }
        return base64(MD5(content, charset), charset);
    }

    /**
     * 向指定 URL 发送POST方法的请求
     * @param url 发送请求的 URL
     * @param params 请求的参数集合
     * @return 远程资源的响应结果
     */
    @SuppressWarnings("unused")
    private static String sendPost(String url, Map<String, String> params) {
        OutputStreamWriter out = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        try {
            URL realUrl = new URL(url);
            HttpURLConnection conn =(HttpURLConnection) realUrl.openConnection();
            // 发送POST请求必须设置如下两行
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // POST方法
            conn.setRequestMethod("POST");
            // 设置通用的请求属性
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.connect();
            // 获取URLConnection对象对应的输出流
            out = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
            // 发送请求参数
            if (params != null) {
                StringBuilder param = new StringBuilder();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if(param.length()>0){
                        param.append("&");
                    }
                    param.append(entry.getKey());
                    param.append("=");
                    param.append(entry.getValue());
                    //System.out.println(entry.getKey()+":"+entry.getValue());
                }
                //System.out.println("param:"+param.toString());
                out.write(param.toString());
            }
            // flush输出流的缓冲
            out.flush();
            // 定义BufferedReader输入流来读取URL的响应
            in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                result.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //使用finally块来关闭输出流、输入流
        finally{
            try{
                if(out!=null){
                    out.close();
                }
                if(in!=null){
                    in.close();
                }
            } catch(IOException ex){
                ex.printStackTrace();
            }
        }
        return result.toString();
    }

    private static final char[] base64EncodeChars = new char[] {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
            'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
            'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
            'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
            'w', 'x', 'y', 'z', '0', '1', '2', '3',
            '4', '5', '6', '7', '8', '9', '+', '/'
    };

    public static String base64Encode(byte[] data) {
        StringBuffer sb = new StringBuffer();
        int len = data.length;
        int i = 0;
        int b1, b2, b3;
        while (i < len) {
            b1 = data[i++] & 0xff;
            if (i == len) {
                sb.append(base64EncodeChars[b1 >>> 2]);
                sb.append(base64EncodeChars[(b1 & 0x3) << 4]);
                sb.append("==");
                break;
            }
            b2 = data[i++] & 0xff;
            if (i == len) {
                sb.append(base64EncodeChars[b1 >>> 2]);
                sb.append(base64EncodeChars[((b1 & 0x03) << 4) | ((b2 & 0xf0) >>> 4)]);
                sb.append(base64EncodeChars[(b2 & 0x0f) << 2]);
                sb.append("=");
                break;
            }
            b3 = data[i++] & 0xff;
            sb.append(base64EncodeChars[b1 >>> 2]);
            sb.append(base64EncodeChars[((b1 & 0x03) << 4) | ((b2 & 0xf0) >>> 4)]);
            sb.append(base64EncodeChars[((b2 & 0x0f) << 2) | ((b3 & 0xc0) >>> 6)]);
            sb.append(base64EncodeChars[b3 & 0x3f]);
        }
        return sb.toString();
    }
}
