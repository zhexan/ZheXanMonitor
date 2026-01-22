package com.example.myprojectbackend.utils;

import java.util.Stack;

public class Const {
    public static final String JWT_BLACK_LIST = "jwt:blacklist";

    public static final String VERIFY_EMAIL_LIMIT = "verify:email:limit:";
    public static final String VERIFY_EMAIL_DATA = "verify:email:data:";

    public final static String FLOW_LIMIT_BLOCK = "flow:block:";
    public final static String FLOW_LIMIT_COUNTER = "flow:counter:";
    public static final int ORDER_LIMIT = -101;
    public static final int ORDER_CORS = -102;

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_CLIENT = "client";

    public static final String MQ_MAIL = "mail";

    public static final String ROLE_DEFAULT = "user";
}