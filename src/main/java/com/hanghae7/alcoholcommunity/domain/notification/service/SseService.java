package com.hanghae7.alcoholcommunity.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hanghae7.alcoholcommunity.domain.common.ResponseDto;
import com.hanghae7.alcoholcommunity.domain.member.entity.Member;
import com.hanghae7.alcoholcommunity.domain.member.repository.MemberRepository;
import com.hanghae7.alcoholcommunity.domain.notification.dto.AbsenceNoticeDto;
import com.hanghae7.alcoholcommunity.domain.notification.dto.AbsenceNoticeListDto;
import com.hanghae7.alcoholcommunity.domain.notification.dto.NoticeParticipantResponseDto;
import com.hanghae7.alcoholcommunity.domain.notification.dto.NoticeResponseDto;
import com.hanghae7.alcoholcommunity.domain.notification.entity.Notice;
import com.hanghae7.alcoholcommunity.domain.notification.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class SseService {
    //private final ObjectMapper objectMapper;
    private final NoticeRepository noticeRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public ResponseEntity<ResponseDto> getAbsenceNotice(Member member){
        List<Notice> noticeList = member.getMemberNotice();
        List<NoticeResponseDto> noticeResponseDtoList = new ArrayList<>();
        List<AbsenceNoticeDto> absenceNoticeDtoList = new ArrayList<>();


        for (Notice notice : noticeList) {
            if (notice.getIsRead()) {
                continue;
            } else {
                // noticeCode = 1 일 경우와 2 일 경우 나눠서 처리

                // if noticeCode == 1 (신청자 알림)
                if(notice.getNoticeCode()==null){
                    continue;
                }
                else if(notice.getNoticeCode()==1){
                    Optional<Member> participant = memberRepository.findById(notice.getParticipantsId());
                    Boolean participatedIs;
                    if(notice.getAccepted()){
                        participatedIs = true;
                    }
                    else{
                        participatedIs = false;
                    }
                    NoticeParticipantResponseDto noticeParticipantResponseDto = new NoticeParticipantResponseDto(
                            notice.getPartyId(),
                            notice.getPartyTitle(),
                            notice.getNoticeCode(),
                            participant.get().getProfileImage(),
                            participant.get().getMemberName(),
                            notice.getParticipantsId(),
                            participatedIs
                    );
                    absenceNoticeDtoList.add(noticeParticipantResponseDto);

                    Notice not = noticeRepository.findByNoticeId(notice.getNoticeId());
                    not.updateRead(true);
                }

                // if noticeCode == 2 (승인거절여부 알림)
                else if(notice.getNoticeCode()==2){
                    NoticeResponseDto noticeResponseDto = new NoticeResponseDto(notice);
                    absenceNoticeDtoList.add(noticeResponseDto);

                    Notice not = noticeRepository.findByNoticeId(notice.getNoticeId());
                    not.updateRead(true);
                }

            }
        }
        return new ResponseEntity<>(new ResponseDto(200, "부재 중 알림 메세지 응답에 성공하였습니다.", new AbsenceNoticeListDto(absenceNoticeDtoList)), HttpStatus.OK);
    }

}