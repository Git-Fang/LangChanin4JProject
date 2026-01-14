package org.fb.constant;


public class BusinessConstant {

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS.contains("win");
    
    public static final String TEMP_FILE_PATH = IS_WINDOWS ? "D:\\个人资料\\uploaded_documents\\" : "/tmp/uploaded_documents/";

    public static final String BAIDU_MAP_MCP_SERVER = "@baidumap/mcp-server-baidu-map";

    public static final String MEDICAL_TYPE = "medical";

    public static final String TRANSLATION_TYPE = "translation";

    public static final String TERM_EXTRACTION_TYPE = "term_extraction";

    public static final String SQL_OPERATION_TYPE = "sql_transfer";

    public static final String DEFAULT_TYPE = "general";


    //  业务使用常量信息
    public static final int MAX_PARAGRAPH_LENGTH = 1500;
    public static final int THREAD_POOL_SIZE = 15;
}
