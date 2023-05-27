package com.hanghae7.alcoholcommunity.domain.party.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanghae7.alcoholcommunity.domain.common.ResponseDto;
import com.hanghae7.alcoholcommunity.domain.member.entity.Member;
import com.hanghae7.alcoholcommunity.domain.party.dto.response.ApproveListDto;
import com.hanghae7.alcoholcommunity.domain.party.dto.response.PartyListResponse;
import com.hanghae7.alcoholcommunity.domain.party.entity.Party;
import com.hanghae7.alcoholcommunity.domain.party.entity.PartyParticipate;
import com.hanghae7.alcoholcommunity.domain.party.repository.PartyParticipateRepository;
import com.hanghae7.alcoholcommunity.domain.party.repository.PartyRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class PartyParticipateService {

	private final PartyParticipateRepository partyParticipateRepository;
	private final PartyRepository partyRepository;

	/**
	 * 모임신청 메소드, 신청 save시 기본 awating값은 True 설정
	 * @param partyId FE에서 매개변수로 전달한 Party의 Id
	 * @param member token을 통해 얻은 Member
	 * @return PartyID와 신청한 Member값 반환
	 */
	@Transactional
	public ResponseEntity<ResponseDto> participateParty(Long partyId, Member member) {

		Party party = new Party();
		try {
			party = partyRepository.findById(partyId).orElseThrow(
				() -> new IllegalArgumentException("존재하지 않는 모임 입니다."));
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 모임 입니다."), HttpStatus.OK);
		}
		Optional<PartyParticipate> participate = partyParticipateRepository.findByPartyAndMember(party, member);
		if (participate.isEmpty()) {
			partyParticipateRepository.save(new PartyParticipate(party, member));
			return new ResponseEntity<>(new ResponseDto(200, "모임 신청에 성공했습니다."), HttpStatus.OK);
		} else if (participate.get().isHost()){
			return new ResponseEntity<>(new ResponseDto(200, "이미 호스트인 모임입니다."), HttpStatus.OK);
		}
		  else if (participate.get().isRejected()) {
			return new ResponseEntity<>(new ResponseDto(200, "거절 된 모임입니다."), HttpStatus.OK);
		} else if (participate.get().isAwaiting()) {
			partyParticipateRepository.delete(participate.get());
			return new ResponseEntity<>(new ResponseDto(200, "모임 신청이 성공적으로 취소되었습니다."), HttpStatus.OK);
		} else {
			System.out.println("이리로 오니 ?");
			partyParticipateRepository.delete(participate.get());
			party.subCurrentCount();
			return new ResponseEntity<>(new ResponseDto(200, "모임 신청이 성공적으로 취소되었습니다."), HttpStatus.OK);
		}
	}

	/**
	 * 주최자가 승인신청 여부판단, 꽉찬 모임이라면 승인안됨
	 * @param participateId 파티신청 정보의 ID
	 * @return 승인여부 리턴
	 */
	@Transactional
	public ResponseEntity<ResponseDto> acceptParty(Long participateId) {

		PartyParticipate participate = new PartyParticipate();
		try {
			participate = partyParticipateRepository.findById(participateId).orElseThrow(
				() -> new IllegalArgumentException("존재하지않는 참여자입니다."));
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 참여자 입니다."), HttpStatus.OK);
		}
		Party party = new Party();
		try {
			party = partyRepository.findById(participate.getParty().getPartyId()).orElseThrow(
				() -> new IllegalArgumentException("존재하지 않는 모임 입니다."));
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 모임 입니다."), HttpStatus.OK);
		}

		if (party.isRecruitmentStatus()) {
			participate.setAwaiting(false);
			party.addCurrentCount();

			//채팅방에 추가해주는 로직추가되야함

			if (party.getCurrentCount() == party.getTotalCount()) {
				party.setRecruitmentStatus(false);
			}
		} else {
			return new ResponseEntity<>(new ResponseDto(200, "이미 꽉찬 모임방 입니다."), HttpStatus.OK);
		}
		return new ResponseEntity<>(new ResponseDto(200, "해당 유저를 승인하였습니다."), HttpStatus.OK);
	}

	/**
	 * 주최자가 대기 인원 중에 승인거부하고 싶은 대기 인원 승인 거부
	 * @param participateId 파티신청 정보의 ID
	 * @return 승인거절 여부 리턴
	 */
	@Transactional
	public ResponseEntity<ResponseDto> removeWaiting(Long participateId) {
		PartyParticipate participate = new PartyParticipate();
		try {
			participate = partyParticipateRepository.findById(participateId).orElseThrow(
				() -> new IllegalArgumentException("존재하지않는 참여자입니다."));
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 참여자 입니다."), HttpStatus.OK);
		}

		participate.setRejection(true);

		return new ResponseEntity<>(new ResponseDto(200, "해당 유저를 승인 거절 하였습니다."), HttpStatus.OK);
	}

	/**
	 * 모임 리스트 (전체/승인완료된리스트/승인대기중인 리스트)
	 * @param approveStatus 0: 전체 리스트 / 1: 승인완료된 모임리스트 / 2: 승인 대기중인 모임 리스트
	 * @param member token을 통해 얻은 Member
	 * @return approveStatus값에 따른 모임리스트 출력
	 */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseDto> getParticipatePartyList(int approveStatus, Member member) {
		List<PartyParticipate> parties;
		if (approveStatus == 0) {
			parties = partyParticipateRepository.findByAllParty(member);
		} else if (approveStatus == 1) {
			parties = partyParticipateRepository.findByAcceptedParty(member);
		} else {
			parties = partyParticipateRepository.findByJoinParty(member);
		}
		List<PartyListResponse> partyList = new ArrayList<>();
		for (PartyParticipate party : parties) {
			int state;
			if (party.isAwaiting()) {
				state = 2;
			} else {
				state = 1;
			}
			PartyListResponse partyResponse = new PartyListResponse(party.getParty(), state);
			List<PartyParticipate> partyParticipates = partyParticipateRepository.findByPartyId(
				party.getParty().getPartyId());
			partyResponse.getparticipateMembers(partyParticipates.stream()
				.map(PartyParticipate::getMember)
				.collect(Collectors.toList()));
			partyList.add(partyResponse);
		}
		return new ResponseEntity<>(new ResponseDto(200, "모임 조회에 성공했습니다.", partyList), HttpStatus.OK);
	}


	/**
	 * 내게 들어온 모임 승인 요청 목록
	 * @param member token을 통해 얻은 Member
	 * @return 승인 요청 된 멤버 리스트 출력
	 */
	public ResponseEntity<ResponseDto> getApproveList(Member member) {

		List<PartyParticipate> parties = partyParticipateRepository.findPartyParticipatesByHostAndMemberId(member);
		List<ApproveListDto> approveMemberList = new ArrayList<>();

		for (PartyParticipate partyParticipate : parties) {
			ApproveListDto approveListDto = new ApproveListDto(partyParticipate);
			approveMemberList.add(approveListDto);
		}
		return new ResponseEntity<>(new ResponseDto(200, "승인요청멤버 조회에 성공했습니다.", approveMemberList), HttpStatus.OK);
	}

	public ResponseEntity<ResponseDto> getHostPartyList(Member member) {
		List<PartyParticipate> parties = partyParticipateRepository.findPartyParticipateByHost(member);
		List<PartyListResponse> partyList = new ArrayList<>();
		for (PartyParticipate party : parties) {
			PartyListResponse partyResponse = new PartyListResponse(party.getParty(), 1);
			List<PartyParticipate> partyParticipates = partyParticipateRepository.findByPartyId(party.getParty().getPartyId());
			partyResponse.getparticipateMembers(partyParticipates.stream()
				.map(PartyParticipate::getMember)
				.collect(Collectors.toList()));
			partyList.add(partyResponse);
		}
		return new ResponseEntity<>(new ResponseDto(200, "회원이 호스트인 모임 조회에 성공했습니다.", partyList), HttpStatus.OK);
	}
}