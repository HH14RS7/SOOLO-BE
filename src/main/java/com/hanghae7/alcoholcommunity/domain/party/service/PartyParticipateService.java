package com.hanghae7.alcoholcommunity.domain.party.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanghae7.alcoholcommunity.domain.common.ResponseDto;
import com.hanghae7.alcoholcommunity.domain.member.entity.Member;
import com.hanghae7.alcoholcommunity.domain.party.dto.response.JoinPartyResponseDto;
import com.hanghae7.alcoholcommunity.domain.party.dto.response.RecruitingPartyResponseDto;
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
				() -> new IllegalArgumentException ("존재하지 않는 모임 입니다."));
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 모임 입니다."), HttpStatus.OK);
		}
		System.out.println("************************1");
		Optional<PartyParticipate> participate = partyParticipateRepository.findByPartyAndMember(party, member);
		System.out.println("************************2");
		if(participate.isEmpty()){
			System.out.println("************************3");
			partyParticipateRepository.save(new PartyParticipate(party, member));
			return new ResponseEntity<>(new ResponseDto(200, "모임 신청에 성공했습니다."), HttpStatus.OK);
		}
		else if(participate.get().isRejected()){
			return new ResponseEntity<>(new ResponseDto(200, "거절 된 모임입니다."), HttpStatus.OK);
		}
		else if(participate.get().isAwaiting()){
			System.out.println(participate.get());
			System.out.println(participate.get().getId());

			partyParticipateRepository.delete(participate.get());
			System.out.println("여긴 지났니?");
			return new ResponseEntity<>(new ResponseDto(200, "모임 신청이 성공적으로 취소되었습니다."), HttpStatus.OK);
		}
		else{
			System.out.println("이리로 오니 ?");
			partyParticipateRepository.delete(participate.get());
			party.subCurrentCount();
			return new ResponseEntity<>(new ResponseDto(200, "모임 신청이 성공적으로 취소되었습니다."), HttpStatus.OK);
		}
	}

	/**
	 * 승인신청 여부, 꽉찬 모임이라면 승인안됨
	 * @param participateId 파티신청 정보의 ID
	 * @return 승인여부 리턴
	 */
	// 주최자가 참여 여부 판단하기
	@Transactional
	public ResponseEntity<ResponseDto> acceptParty(Long participateId){

		PartyParticipate participate = new PartyParticipate();
		try {
			participate = partyParticipateRepository.findById(participateId).orElseThrow(
				() -> new IllegalArgumentException("존재하지않는 참여자입니다."));
		}catch (IllegalArgumentException e){
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 참여자 입니다."), HttpStatus.OK);
		}
		Party party = new Party();
		try {
			party = partyRepository.findById(participate.getParty().getPartyId()).orElseThrow(
				() -> new IllegalArgumentException ("존재하지 않는 모임 입니다."));
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 모임 입니다."), HttpStatus.OK);
		}

		if(party.isRecruitmentStatus()){
			participate.setAwaiting(false);
			party.addCurrentCount();
			//채팅방에 추가해주는 로직추가되야함
			if(party.getCurrentCount() == party.getTotalCount()){
				party.setRecruitmentStatus(false);
			}
		}
		else{
			return new ResponseEntity<>(new ResponseDto(200, "이미 꽉찬 모임방 입니다."), HttpStatus.OK);
		}
		return new ResponseEntity<>(new ResponseDto(200, "해당 유저를 승인하였습니다."), HttpStatus.OK);
	}

	// 주최자가 대기 인원 중에 삭제하고 싶은 대기 인원 삭제 메서드(승인거부)
	@Transactional
	public ResponseEntity<ResponseDto> removeWaiting(Long participateId){
		PartyParticipate participate = new PartyParticipate();
		try {
			participate = partyParticipateRepository.findById(participateId).orElseThrow(
				() -> new IllegalArgumentException("존재하지않는 참여자입니다."));
		}catch (IllegalArgumentException e){
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 참여자 입니다."), HttpStatus.OK);
		}

		participate.setRejection(true);

		return new ResponseEntity<>(new ResponseDto(200, "해당 유저를 승인 거절 하였습니다."), HttpStatus.OK);
	}

	// 참여중인 party 리스트를 불러올 때 멤버의 partyParticipate 리스트를 불러와서 true인 경우만 따로 빼내서 응답할 수 있는 메서드 필요
	// 모임 신청 대기 목록 (승인대기중)
	@Transactional(readOnly = true)
	public ResponseEntity<List<RecruitingPartyResponseDto>> getJoinPartyList(){
		List<RecruitingPartyResponseDto> joinPartyList = partyRepository.getAllJoinParty();
		return new ResponseEntity<>(joinPartyList, HttpStatus.OK);
	}

	// 참여중인 party 리스트 (채팅방까지 들어간 모임) 참여자입장(주최자입장x)
	@Transactional(readOnly = true)
	public ResponseEntity<List<JoinPartyResponseDto>> getParticipatePartyList(Member member){
		List<JoinPartyResponseDto> participatePartyList = partyParticipateRepository.getAllParticipateParty(member);
		return new ResponseEntity<>(participatePartyList, HttpStatus.OK);
	}
}