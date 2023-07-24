package com.hanghae7.alcoholcommunity.domain.party.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import com.hanghae7.alcoholcommunity.domain.common.entity.S3Service;
import com.hanghae7.alcoholcommunity.domain.notification.repository.NoticeRepository;
import com.hanghae7.alcoholcommunity.domain.party.dto.response.PartyResponseDto;
import com.hanghae7.alcoholcommunity.domain.party.entity.Party;
import com.hanghae7.alcoholcommunity.domain.party.entity.PartyParticipate;
import com.hanghae7.alcoholcommunity.domain.party.repository.PartyParticipateRepository;
import com.hanghae7.alcoholcommunity.domain.party.repository.PartyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hanghae7.alcoholcommunity.domain.chat.entity.ChatMessage;
import com.hanghae7.alcoholcommunity.domain.chat.entity.ChatRoom;
import com.hanghae7.alcoholcommunity.domain.common.ResponseDto;
import com.hanghae7.alcoholcommunity.domain.common.jwt.JwtUtil;
import com.hanghae7.alcoholcommunity.domain.member.entity.Member;

import com.hanghae7.alcoholcommunity.domain.member.repository.MemberRepository;
import com.hanghae7.alcoholcommunity.domain.party.dto.request.PartyRequestDto;
import com.hanghae7.alcoholcommunity.domain.party.dto.response.PartyListResponse;
import com.hanghae7.alcoholcommunity.domain.party.dto.response.PartyListResponseDto;

import com.hanghae7.alcoholcommunity.domain.chat.repository.ChatMessageRepository;
import com.hanghae7.alcoholcommunity.domain.chat.repository.ChatRoomRepository;


import lombok.RequiredArgsConstructor;

/**
 * Please explain the class!!
 *
 * @fileName      : party service
 * @author        : mycom
 * @since         : 2023-05-19
 */

@RequiredArgsConstructor
@EnableScheduling
@Service
public class PartyService {

	private final PartyRepository partyRepository;
	private final PartyParticipateRepository partyParticipateRepository;
	private final MemberRepository memberRepository;
	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final NoticeRepository noticeRepository;
	private final JwtUtil jwtUtil;

	private final S3Service s3Service;

	/**
	 * 모임 게시글 등록
	 * @param partyRequestDto 유저 입력값
	 * @param member token을 통해 얻은 Member
	 * @return 모임 생성 유무
	 */
	@Transactional
	public ResponseEntity<ResponseDto> createParty(PartyRequestDto partyRequestDto, Member member, MultipartFile image) throws
		IOException {
		if(member.getAuthority().equals("BLOCK")){
			return new ResponseEntity<>(new ResponseDto(400, "정지된 아이디 입니다."), HttpStatus.OK);
		}

		Party party = new Party(partyRequestDto, member.getMemberName(), member.getMemberUniqueId());

		if(image != null){
			if(!imageTypeChecker(image)){
				return new ResponseEntity<>(new ResponseDto(400, "허용되지 않는 이미지 확장자입니다. 허용되는 확장자 : JPG, JPEG, PNG, GIF, BMP, WEBP, SVG"), HttpStatus.BAD_REQUEST);
			}
			party.setImageUrl(s3Service.upload(image));
		}

		//모임만들때 채팅룸 생성
		ChatRoom chatRoom = ChatRoom.create(partyRequestDto.getTitle());
		chatRoomRepository.save(chatRoom);
		ChatMessage chatMessage = new ChatMessage(ChatMessage.MessageType.ENTER, chatRoom.getChatRoomUniqueId(), member, "채팅방이 생성되었습니다", LocalDateTime.now(), chatRoom);
		chatMessageRepository.save(chatMessage);
		PartyParticipate partyParticipate = new PartyParticipate(party, member, true, false, chatRoom);
		party.addCurrentCount();
		party.setRecruitmentStatus(true);
		partyRepository.save(party);
		partyParticipateRepository.save(partyParticipate);
		return new ResponseEntity<>(new ResponseDto(200, "모임 생성에 성공했습니다."), HttpStatus.OK);
	}

	/**
	 * ???????????????????????????
	 * 모임 전체조회(전체/모집중/모집마감)
	 *
	 * @param page              요청한 페이지 번호
	 * @param recruitmentStatus 0: 전체 리스트 / 1: 승인완료된 모임리스트 / 2: 승인 대기중인 모임 리스트
	 * @param request           토큰값을 확인하기 위한 정보
	 * @return 각 리스트 출력
	 */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseDto> findAll(double radius, double longitude, double latitude, int page, int recruitmentStatus, HttpServletRequest request) {

		String accessToken = request.getHeader("Access_key");
		String memberUniqueId = null;
		if (accessToken != null) {
			memberUniqueId = jwtUtil.getMemberInfoFromToken(accessToken.substring(7));
			if(memberRepository.findByMemberUniqueId(memberUniqueId).get().getAuthority().equals("BLOCK")){
				return new ResponseEntity<>(new ResponseDto(400, "정지된 아이디 입니다."), HttpStatus.OK);
			}
		}
		List<Party> parties;
		Pageable pageable = PageRequest.of(page, 10);
		if(recruitmentStatus == 0){
			parties = partyRepository.findAllByisDeletedFalseOrderByCreatedAtDesc(pageable);
		}else if(recruitmentStatus == 1){
			parties = partyRepository.findAllByisDeletedFalseAndRecruitmentStatusOrderByCreatedAtDesc(true, pageable);
		} else {
			parties = partyRepository.findAllByisDeletedFalseAndRecruitmentStatusOrderByCreatedAtDesc(false, pageable);
		}

		List<PartyListResponse> partyList = new ArrayList<>();
		if(memberUniqueId == null) {
				for (Party party : parties) {
					PartyListResponse partyResponse = new PartyListResponse(party);
					List<PartyParticipate> partyParticipates = partyParticipateRepository.findByisDeletedFalseAndAwaitingFalseAndRejectedFalseAndPartyPartyIdOrderByHostDesc(party.getPartyId());
					partyResponse.getparticipateMembers(partyParticipates.stream()
						.map(PartyParticipate::getMember)
						.collect(Collectors.toList()));
					double distanceFromCoordinate =distanceCalculator(latitude,longitude,partyResponse.getLatitude(),partyResponse.getLongitude());
					if(distanceFromCoordinate<= radius){
						partyResponse.setDistanceCal(distanceFromCoordinate);
						partyList.add(partyResponse);}
				}

		}

		else{
			for (Party party : parties) {
				Optional<Member> searchMember = memberRepository.findByMemberUniqueId(memberUniqueId);
				if(searchMember.isPresent()){
					int state = getState(party, searchMember.get());
					PartyListResponse partyResponse = new PartyListResponse(party, state);
					List<PartyParticipate> partyParticipates = partyParticipateRepository.findByisDeletedFalseAndAwaitingFalseAndPartyPartyIdOrderByHostDesc(party.getPartyId());
					partyResponse.getparticipateMembers(partyParticipates.stream()
						.map(PartyParticipate::getMember)
						.collect(Collectors.toList()));
					double distanceFromCoordinate =distanceCalculator(latitude,longitude,partyResponse.getLatitude(),partyResponse.getLongitude());
					if(distanceFromCoordinate<= radius){
						partyResponse.setDistanceCal(distanceFromCoordinate);
						partyList.add(partyResponse);}
				}
			}

		}

		return new ResponseEntity<>(new ResponseDto(200, "모임 조회에 성공했습니다.", new PartyListResponseDto(partyList, page, partyList.size())), HttpStatus.OK);
	}


	/**
	 * 모임 상세조회
	 * @param partyId FE에서 매개변수로 전달한 Party의 Id
	 * @param member token을 통해 얻은 Member
	 * @return 모임 게시글에 속한 모든 내용
	 */

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseDto> getParty(Long partyId, Member member) {
		if(member.getAuthority().equals("BLOCK")){
			return new ResponseEntity<>(new ResponseDto(400, "정지된 아이디 입니다."), HttpStatus.OK);
		}
		Party party = new Party();
		try {
			party = partyRepository.findById(partyId).orElseThrow(
				() -> new IllegalArgumentException("해당 모임이 존재하지 않습니다."));
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "해당 모임이 존재하지 않습니다."), HttpStatus.OK);
		}
		List<PartyParticipate> partyMember = partyParticipateRepository.findByisDeletedFalseAndAwaitingFalseAndPartyPartyIdOrderByHostDesc(partyId);
		int state = getState(party, member);
		PartyResponseDto partyResponseDto = new PartyResponseDto(party,state);
		partyResponseDto.getparticipateMembers(partyMember);
		return new ResponseEntity<>(new ResponseDto(200, "모임 상세 조회에 성공하였습니다.", partyResponseDto), HttpStatus.OK);
	}

	/**
	 * 모임 게시글 수정
	 * @param partyId FE에서 매개변수로 전달한 Party의 Id
	 * @param partyRequestDto 유저 입력값
	 * @param member token을 통해 얻은 Member
	 * @return 수정 성공 유무
	 */
	@Transactional
	public ResponseEntity<ResponseDto> updateParty(Long partyId, PartyRequestDto partyRequestDto, Member member, MultipartFile image) throws
		IOException {
		if(member.getAuthority().equals("BLOCK")){
			return new ResponseEntity<>(new ResponseDto(400, "정지된 아이디 입니다."), HttpStatus.OK);
		}
		Party party = new Party();
		try {
			party = partyRepository.findById(partyId).orElseThrow(
				() -> new IllegalArgumentException("존재하지 않는 모임 입니다."));
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 모임 입니다."), HttpStatus.OK);
		}
		Member hostMember = new Member();
		try {
			PartyParticipate participate1 = partyParticipateRepository.findMemberByisDeletedFalseAndHostTrueAndPartyPartyId(partyId).orElseThrow(
				() -> new IllegalArgumentException("호스트를 찾을 수 없습니다."));
			hostMember = participate1.getMember();
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "호스트를 찾을 수 없습니다."), HttpStatus.OK);

		}


		if (!hostMember.getMemberUniqueId().equals(member.getMemberUniqueId())) {
			return new ResponseEntity<>(new ResponseDto(400, "다른 회원이 개설한 모임입니다."), HttpStatus.OK);
		} else {
			if(image != null){
				if(!imageTypeChecker(image)){
					return new ResponseEntity<>(new ResponseDto(400, "허용되지 않는 이미지 확장자입니다. 허용되는 확장자 : JPG, JPEG, PNG, GIF, BMP, WEBP, SVG"), HttpStatus.BAD_REQUEST);
				}
				party.setImageUrl(s3Service.upload(image));
			}

			party.updateParty(partyRequestDto);
			/*PartyParticipate partyParticipate = partyParticipateRepository.findByisDeletedFalseAndHostTrueAndParty(party);
			chatRoomRepository.updateChatRoomTitle(partyParticipate.getChatRoom().getChatRoomUniqueId(), partyRequestDto.getTitle());*/

		}
		return new ResponseEntity<>(new ResponseDto(200, "모임을 수정하였습니다."), HttpStatus.OK);
	}

	/**
	 * 모임 게시글 삭제
	 * @param partyId FE에서 매개변수로 전달한 Party의 Id
	 * @param member token을 통해 얻은 Member
	 * @return 삭제 성공 유무
	 */
	@Transactional
	public ResponseEntity<ResponseDto> deleteParty(Long partyId, Member member) {
		if(member.getAuthority().equals("BLOCK")){
			return new ResponseEntity<>(new ResponseDto(400, "정지된 아이디 입니다."), HttpStatus.OK);
		}
		Party party = new Party();
		try {
			party = partyRepository.findById(partyId).orElseThrow(
				() -> new IllegalArgumentException ("존재하지 않는 모임 입니다."));
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "존재하지 않는 모임 입니다."), HttpStatus.OK);
		}
		Member hostMember = new Member();
		try {
			PartyParticipate participate1 = partyParticipateRepository.findMemberByisDeletedFalseAndHostTrueAndPartyPartyId(partyId).orElseThrow(
				() -> new IllegalArgumentException("호스트를 찾을 수 없습니다."));
			hostMember = participate1.getMember();
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(new ResponseDto(400, "호스트를 찾을 수 없습니다."), HttpStatus.OK);
		}
		if (!hostMember.getMemberUniqueId().equals(member.getMemberUniqueId())) {
			return new ResponseEntity<>(new ResponseDto(400, "해당 사용자가 아닙니다."), HttpStatus.BAD_REQUEST);
		} else {
			PartyParticipate partyParticipate = partyParticipateRepository.findByisDeletedFalseAndHostTrueAndParty(party);
			partyRepository.softDeleteParty(party.getPartyId());
			chatMessageRepository.softDeleteByChatRoomUniqueId(partyParticipate.getChatRoom().getChatRoomUniqueId());
			chatRoomRepository.softDeleteChatRoom(partyParticipate.getChatRoom().getChatRoomId());
			partyParticipateRepository.softDeletepartyId(party.getPartyId());
			noticeRepository.deleteAllByPartyId(partyId);
		}
		return new ResponseEntity<>(new ResponseDto(200, "모임을 삭제하였습니다."), HttpStatus.OK);
	}

	/**
	 *  ???????????????????????????
	 * @param party partyId
	 * @param member FE에서 매개변수로 전달한 Party의 Id
	 * @return ??????????
	 */
	public int getState(Party party, Member member) {
		Optional<PartyParticipate> participate = partyParticipateRepository.findByisDeletedFalseAndPartyAndMember(party, member);
		if (participate.isPresent()) {
			if (participate.get().isRejected()) {
				return 3;
			} else if (participate.get().isAwaiting()) {
				return 2;
			} else {
				return 1;
			}
		} else {
			return 0;
		}
	}

	@Scheduled(fixedRate  = 600000)
	@Transactional
	public void deleteTimeoverParty(){
		LocalDateTime timenow = LocalDateTime.now().plusHours(9);
		LocalDateTime result = timenow.minusHours(4);
		List<Party> partyList = partyRepository.findAllByPartyDateBefore(result);
		for (Party party : partyList) {
			PartyParticipate partyParticipate = partyParticipateRepository.findByisDeletedFalseAndHostTrueAndParty(party);
			partyRepository.softDeleteParty(party.getPartyId());
			if(partyParticipate != null) {
				chatMessageRepository.softDeleteByChatRoomUniqueId(partyParticipate.getChatRoom().getChatRoomUniqueId());
				chatRoomRepository.softDeleteChatRoom(partyParticipate.getChatRoom().getChatRoomId());
			}
			partyParticipateRepository.softDeletepartyId(party.getPartyId());
		}
	}

	public double distanceCalculator(double latitude, double longitude, double latitude2, double longitude2){
		final int R = 6371;
		double dLat = Math.toRadians(latitude2 - latitude);
		double dLon = Math.toRadians(longitude2- longitude);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
			+ Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(latitude2))
			* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

		double distance = R * c;

		return distance;
	}

	/**
	 * ???????????????????????????
	 * 모임 검색 조회(전체/모집중/모집마감)
	 *
	 * @param page              요청한 페이지 번호
	 * @param recruitmentStatus 0: 전체 리스트 / 1: 승인완료된 모임리스트 / 2: 승인 대기중인 모임 리스트
	 * @param request           토큰값을 확인하기 위한 정보
	 * @param keyword			검색어
	 * @return 각 리스트 출력
	 */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseDto> findAllSearch(double radius, double longitude, double latitude, int page, int recruitmentStatus, HttpServletRequest request,
		String keyword) {

		String accessToken = request.getHeader("Access_key");
		String memberUniqueId = null;
		if (accessToken != null) {
			memberUniqueId = jwtUtil.getMemberInfoFromToken(accessToken.substring(7));
			if(memberRepository.findByMemberUniqueId(memberUniqueId).get().getAuthority().equals("BLOCK")){
					return new ResponseEntity<>(new ResponseDto(400, "정지된 아이디 입니다."), HttpStatus.OK);
			}
		}

		Pageable pageable = PageRequest.of(page, 10);
		List<Party> parties;
		if(recruitmentStatus == 0){
			parties = partyRepository.findAllPartyByKeyword(pageable, keyword);
		}else if(recruitmentStatus == 1){
			parties = partyRepository.findAllPartyByKeywordRecruitmentStatus(true, pageable, keyword);
		} else {
			parties = partyRepository.findAllPartyByKeywordRecruitmentStatus(false, pageable, keyword);
		}

		List<PartyListResponse> partyList = new ArrayList<>();
		if(memberUniqueId == null) {

			for (Party party : parties) {
				PartyListResponse partyResponse = new PartyListResponse(party);
				List<PartyParticipate> partyParticipates = partyParticipateRepository.findByisDeletedFalseAndAwaitingFalseAndPartyPartyIdOrderByHostDesc(party.getPartyId());
				partyResponse.getparticipateMembers(partyParticipates.stream()
					.map(PartyParticipate::getMember)
					.collect(Collectors.toList()));
				double distanceFromCoordinate =distanceCalculator(latitude,longitude,partyResponse.getLatitude(),partyResponse.getLongitude());
				if(distanceFromCoordinate<= radius){
					partyResponse.setDistanceCal(distanceFromCoordinate);
					partyList.add(partyResponse);}
			}

		}

		else{
			for (Party party : parties) {
				Optional<Member> searchMember = memberRepository.findByMemberUniqueId(memberUniqueId);
				if(searchMember.isPresent()){
					int state = getState(party, searchMember.get());
					PartyListResponse partyResponse = new PartyListResponse(party, state);
					List<PartyParticipate> partyParticipates = partyParticipateRepository.findByisDeletedFalseAndAwaitingFalseAndPartyPartyIdOrderByHostDesc(party.getPartyId());
					partyResponse.getparticipateMembers(partyParticipates.stream()
						.map(PartyParticipate::getMember)
						.collect(Collectors.toList()));
					double distanceFromCoordinate =distanceCalculator(latitude,longitude,partyResponse.getLatitude(),partyResponse.getLongitude());
					if(distanceFromCoordinate<= radius){
						partyResponse.setDistanceCal(distanceFromCoordinate);
						partyList.add(partyResponse);}
				}
			}

		}

		return new ResponseEntity<>(new ResponseDto(200, "모임 조회에 성공했습니다.", new PartyListResponseDto(partyList, page, partyList.size())), HttpStatus.OK);
	}

	@Transactional(readOnly = true)
	public ResponseEntity<Void> forTest(){
		return ResponseEntity.ok().build();
	}

	private boolean imageTypeChecker(MultipartFile image) {
		String imageType [] = {"jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"};
		for(String type : imageType){
			if(image.getContentType().contains(type)){
				return true;
			}
		}
		return false;
	}
}





