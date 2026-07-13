package com.ahao.haochat.common.chat.service.strategy.msg;

import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chat.domain.entity.msg.ImgMsgDTO;
import com.ahao.haochat.common.chat.domain.entity.msg.MessageExtra;
import com.ahao.haochat.common.chat.domain.enums.MessageTypeEnum;
import com.ahao.haochat.common.file.domain.FileScene;
import com.ahao.haochat.common.file.service.FileAssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Description:图片消息
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-06-04
 */
@Component
public class ImgMsgHandler extends AbstractMsgHandler<ImgMsgDTO> {
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private FileAssetService fileAssetService;

    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.IMG;
    }

    @Override
    public void saveMsg(Message msg, ImgMsgDTO body) {
        MessageExtra extra = Optional.ofNullable(msg.getExtra()).orElse(new MessageExtra());
        Message update = new Message();
        update.setId(msg.getId());
        update.setExtra(extra);
        extra.setImgMsgDTO(body);
        messageDao.updateById(update);
    }

    @Override
    protected void checkMsg(ImgMsgDTO body, Long roomId, Long uid) {
        fileAssetService.assertAttachable(uid, roomId, body.getAssetId(), FileScene.CHAT_IMAGE);
    }

    @Override
    public Object showMsg(Message msg) {
        return msg.getExtra().getImgMsgDTO();
    }

    @Override
    public Object showReplyMsg(Message msg) {
        return "图片";
    }

    @Override
    public String showContactMsg(Message msg) {
        return "[图片]";
    }
}
