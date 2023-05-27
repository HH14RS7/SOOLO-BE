package com.hanghae7.alcoholcommunity.domain.sociallogin.naverlogin.dto;

import java.net.URI;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.hanghae7.alcoholcommunity.domain.sociallogin.kakaologin.dto.KakaoInfo;
import com.hanghae7.alcoholcommunity.domain.sociallogin.kakaologin.dto.KakaoToken;
import com.hanghae7.alcoholcommunity.domain.sociallogin.naverlogin.client.NaverClient;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Please explain the class!!
 *
 * @fileName      : NaverToken
 * @author        : mycom
 * @since         : 2023-05-22
 */
@ToString
@Getter
@NoArgsConstructor
@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NaverToken {
	private String tokenType;
	private String accessToken;
	private String refreshToken;
	private Long expiresIn;

	public static NaverToken fail() {
		return new NaverToken(null, null);
	}

	private NaverToken(final String accessToken, final String refreshToken) {

		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
	}


}
