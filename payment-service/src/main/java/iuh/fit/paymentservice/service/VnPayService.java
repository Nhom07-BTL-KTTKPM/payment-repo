package iuh.fit.paymentservice.service;

import iuh.fit.paymentservice.dto.OrderResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class VnPayService {

    @Value("${vnpay.tmn-code}")
    private String vnpTmnCode;

    @Value("${vnpay.hash-secret}")
    private String vnpHashSecret;

    @Value("${vnpay.url}")
    private String vnpPayUrl;

    @Value("${vnpay.return-url}")
    private String vnpReturnUrl;

    public String createPaymentUrl(HttpServletRequest req, long amount, String orderInfo) {
        String vnpVersion = "2.1.0";
        String vnpCommand = "pay";
        String orderType = req.getParameter("ordertype");
        String vnpTxnRef = getRandomNumber(8);
        String vnpIpAddr = getIpAddress(req);

        amount = amount * 100;
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnpVersion);
        vnpParams.put("vnp_Command", vnpCommand);
        vnpParams.put("vnp_TmnCode", vnpTmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(amount));
        vnpParams.put("vnp_CurrCode", "VND");

        String bankCode = req.getParameter("bankcode");
        if (bankCode != null && !bankCode.isEmpty()) {
            vnpParams.put("vnp_BankCode", bankCode);
        }
        vnpParams.put("vnp_TxnRef", vnpTxnRef);
        vnpParams.put("vnp_OrderInfo", orderInfo);
        vnpParams.put("vnp_OrderType", orderType != null ? orderType : "other");

        String locate = req.getParameter("language");
        if (locate != null && !locate.isEmpty()) {
            vnpParams.put("vnp_Locale", locate);
        } else {
            vnpParams.put("vnp_Locale", "vn");
        }
        vnpParams.put("vnp_ReturnUrl", vnpReturnUrl);
        vnpParams.put("vnp_IpAddr", vnpIpAddr);

        TimeZone vietnamTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        Calendar cld = Calendar.getInstance(vietnamTimeZone);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(vietnamTimeZone);
        String vnpCreateDate = formatter.format(cld.getTime());
        vnpParams.put("vnp_CreateDate", vnpCreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnpExpireDate = formatter.format(cld.getTime());
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);

        vnpParams.put("vnp_Bill_Mobile", getSafeParameter(req, "txt_billing_mobile"));
        vnpParams.put("vnp_Bill_Email", getSafeParameter(req, "txt_billing_email"));

        String fullName = getSafeParameter(req, "txt_billing_fullname");
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] nameParts = fullName.trim().split("\\s+", 2);
            if (nameParts.length >= 2) {
                vnpParams.put("vnp_Bill_FirstName", nameParts[0]);
                vnpParams.put("vnp_Bill_LastName", nameParts[nameParts.length - 1]);
            } else {
                vnpParams.put("vnp_Bill_FirstName", fullName);
                vnpParams.put("vnp_Bill_LastName", "");
            }
        } else {
            vnpParams.put("vnp_Bill_FirstName", "");
            vnpParams.put("vnp_Bill_LastName", "");
        }

        vnpParams.put("vnp_Bill_Address", getSafeParameter(req, "txt_inv_addr1"));
        vnpParams.put("vnp_Bill_City", getSafeParameter(req, "txt_bill_city"));
        vnpParams.put("vnp_Bill_Country", getSafeParameter(req, "txt_bill_country"));
        vnpParams.put("vnp_Bill_State", getSafeParameter(req, "txt_bill_state"));

        vnpParams.put("vnp_Inv_Phone", getSafeParameter(req, "txt_inv_mobile"));
        vnpParams.put("vnp_Inv_Email", getSafeParameter(req, "txt_inv_email"));
        vnpParams.put("vnp_Inv_Customer", getSafeParameter(req, "txt_inv_customer"));
        vnpParams.put("vnp_Inv_Address", getSafeParameter(req, "txt_inv_addr1"));
        vnpParams.put("vnp_Inv_Company", getSafeParameter(req, "txt_inv_company"));
        vnpParams.put("vnp_Inv_Taxcode", getSafeParameter(req, "txt_inv_taxcode"));
        vnpParams.put("vnp_Inv_Type", getSafeParameter(req, "cbo_inv_type"));

        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(urlEncode(fieldValue));

                query.append(urlEncode(fieldName));
                query.append('=');
                query.append(urlEncode(fieldValue));

                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnpSecureHash = hmacSHA512(vnpHashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;

        return vnpPayUrl + "?" + queryUrl;
    }

    public String createPaymentUrlForOrder(HttpServletRequest req, OrderResponse order) {
        if (order == null || order.id() == null) {
            throw new IllegalArgumentException("order is required");
        }
        String orderInfo = "Thanh toan don hang " + order.id();
        long amount = order.total() == null ? 0 : order.total().longValue();
        return createPaymentUrl(req, amount, orderInfo);
    }

    private String getSafeParameter(HttpServletRequest req, String paramName) {
        String value = req.getParameter(paramName);
        return value != null ? value : "";
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.US_ASCII.toString());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private String getRandomNumber(int length) {
        Random rnd = new Random();
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String getIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }
        if (ipAddress == null || ipAddress.isEmpty() || ipAddress.equals("0:0:0:0:0:0:0:1")) {
            ipAddress = "127.0.0.1";
        }
        return ipAddress;
    }

    private String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final javax.crypto.Mac hmac512 = javax.crypto.Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes();
            final javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(hmacKeyBytes,
                    "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();

        } catch (Exception ex) {
            return "";
        }
    }

    public int orderReturn(HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();

        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String paramValue = request.getParameter(paramName);
            fields.put(paramName, paramValue);
        }

        String vnpSecureHash = request.getParameter("vnp_SecureHash");

        fields.remove("vnp_SecureHashType");
        fields.remove("vnp_SecureHash");

        String signValue = hashAllFields(fields);

        boolean hashValid = signValue.equals(vnpSecureHash);

        if (hashValid) {
            String transactionStatus = request.getParameter("vnp_TransactionStatus");
            String responseCode = request.getParameter("vnp_ResponseCode");

            if ("00".equals(transactionStatus) && "00".equals(responseCode)) {
                return 1;
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    private String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();

        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                sb.append(fieldName);
                sb.append("=");
                sb.append(urlEncode(fieldValue));
                if (itr.hasNext()) {
                    sb.append("&");
                }
            }
        }
        return hmacSHA512(vnpHashSecret, sb.toString());
    }
}
