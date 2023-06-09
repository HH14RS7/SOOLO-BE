package com.hanghae7.alcoholcommunity.domain.chat.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hanghae7.alcoholcommunity.domain.common.entity.Timestamped;
import com.hanghae7.alcoholcommunity.domain.member.entity.Member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Entity
@RequiredArgsConstructor
public class ChatMessage extends Timestamped implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ChatMessageId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIgnore
    private ChatRoom chatRoom;
    // 메시지 타입 : 입장, 채팅

    private Long memberId;
    private String memberUniqueId;
    private MessageType type; // 메시지 타입
    private String chatRoomUniqueId; // 방번호
    private String sender; // 메시지 보낸사람
    private String memberProfileImage; //프로필 이미지
    private String message; // 메// 시지
    private LocalDateTime createdAt;

    private boolean isDeleted = false;

    public enum MessageType {
        ENTER, EXIT
    }

    public ChatMessage(MessageType type, String chatRoomUniqueId, Member member, String message, LocalDateTime createdAt, ChatRoom chatRoom) {
        this.type = type;
        this.chatRoomUniqueId = chatRoomUniqueId;
        this.memberId = member.getMemberId();
        this.memberUniqueId = member.getMemberUniqueId();
        this.sender = member.getMemberName();
        this.memberProfileImage = member.getProfileImage();
        this.message = message;
        this.createdAt = createdAt;
        this.chatRoom = chatRoom;
    }

}
