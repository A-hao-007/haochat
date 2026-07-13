package com.ahao.haochat.common.file.domain;
import java.util.*;
public enum FileScene { AVATAR("avatar",5L*1024*1024,true,true), CHAT_IMAGE("chat-image",20L*1024*1024,true,false), CHAT_FILE("chat-file",100L*1024*1024,false,false);
 public final String code; public final long maxSize; public final boolean imageOnly; public final boolean publicRead;
 FileScene(String code,long maxSize,boolean imageOnly,boolean publicRead){this.code=code;this.maxSize=maxSize;this.imageOnly=imageOnly;this.publicRead=publicRead;}
 public static FileScene of(String code){return Arrays.stream(values()).filter(v->v.code.equals(code)).findFirst().orElse(null);}}
