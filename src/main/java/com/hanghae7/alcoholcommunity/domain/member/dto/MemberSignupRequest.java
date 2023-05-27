package com.hanghae7.alcoholcommunity.domain.member.dto;

import java.time.LocalDateTime;

import javax.persistence.Column;

import com.hanghae7.alcoholcommunity.domain.common.entity.Timestamped;
import com.hanghae7.alcoholcommunity.domain.member.entity.Member;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Member Entity무결성을 위한 Dto생성
 * @fileName      : MemberSignupRequest
 * @author        : mycom
 * @since         : 2023-05-23
 */
@Getter
@ToString
public class MemberSignupRequest extends Timestamped {

	private String memberEmailId;

	private String memberUniqueId;

	private String gender;

	private String memberName;

	private String profileImage;

	private String social;

	private LocalDateTime createdAt;


	// 무결성을위해서 Builder 사용
	@Builder
	public MemberSignupRequest(String memberEmailId, String memberUniqueId, String gender, String memberName, String profileImage, String social, LocalDateTime createdAt) {
		this.memberEmailId = memberEmailId;
		this.memberUniqueId = memberUniqueId;
		this.gender = gender;
		this.memberName = memberName;
		this.profileImage = profileImage;
		this.social = social;
		this.createdAt = createdAt;
	}
}
