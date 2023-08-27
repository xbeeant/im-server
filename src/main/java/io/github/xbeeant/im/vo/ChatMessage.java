package io.github.xbeeant.im.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class ChatMessage {
    private Long id;

    private String type;

    private String content;

    private String contentType;

    private String creator;

    private Date createAt;

}
