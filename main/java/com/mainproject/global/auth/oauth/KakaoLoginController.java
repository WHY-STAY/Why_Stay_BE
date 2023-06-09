package com.mainproject.global.auth.oauth;

import com.mainproject.domain.member.dto.MemberDto;
import com.mainproject.domain.member.entity.Member;
import com.mainproject.domain.member.repository.MemberRepository;
import com.mainproject.global.auth.JwtProvider;
import com.mainproject.global.auth.redis.AuthService;
import com.mainproject.global.auth.redis.TokenDto;
import com.mainproject.global.exception.BusinessLogicException;
import com.mainproject.global.exception.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@RestController
public class KakaoLoginController {
    private final KakaoLoginService kakaoLoginService;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;

    public KakaoLoginController(KakaoLoginService kakaoLoginService, MemberRepository memberRepository, JwtProvider jwtProvider) {
        this.kakaoLoginService = kakaoLoginService;
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
    }

    @GetMapping("auth/kakao/login")
    public ResponseEntity<?> kakaoLogin(@RequestParam(required = false)String code) {
        KakaoOauth kakaoOauth = kakaoLoginService.getKakaoAccessToken(code);

        HashMap<String ,String> user = kakaoLoginService.getUserInfo(kakaoOauth.getAccessToken());
        log.info("카카오 로그인 시 넘어오는 이미지 url: {}", user.get("image"));

        if(user.get("image") == null) {
            log.info("이미지가 없어서 \"no image\"로 대체합니다");
            user.put("image", "https://postfiles.pstatic.net/MjAyMDExMDFfODMg/MDAxNjA0MjI4ODc1MDgz.gQ3xcHrLXaZyxcFAoEcdB7tJWuRs7fKgOxQwPvsTsrUg.0OBtKHq2r3smX5guFQtnT7EDwjzksz5Js0wCV4zjfpcg.JPEG.gambasg/유튜브_기본프로필_보라.jpg");
        }
        if(user.get("email") == null) {
            log.info("이메일이 없으면 안되는데 일단 없으면 qwerasdf@gmail.com으로 대체");
            user.put("email", "nonamenono@gmail.com"+ generateRandomId());
        }
        if(user.get("nickname") == null) {
            log.info("이름 없으면 \"이름없음\" 으로 대체");
            user.put("nickname", "이름없음");
        }

        MemberDto.OauthPost post = new MemberDto.OauthPost(user.get("email"), user.get("nickname"), user.get("image"));
        log.info("kakao login에서 post할 때 뜨는 email: {}", post.getEmail());
        Optional<Member> findMember = memberRepository.findByEmail(post.getEmail());
        if(findMember.isPresent()) {
            // 가입없이 로그인 진행
            String accessToken = "Bearer " + jwtProvider.createAccessToken(findMember.get());
            String refreshToken = jwtProvider.createRefreshToken(findMember.get());

            KakaoAuthDto response = new KakaoAuthDto(findMember.get().getMemberId(), accessToken, refreshToken, kakaoOauth.getAccessToken());
            return new ResponseEntity<>(response, HttpStatus.OK);

        }
        else {
            // 비밀번호 없이 email과 name으로 회원가입 하기
            // 기존 이메일과 새로 가입하려는 카카오 이메일이 일치하면 ...
            user.put("message", "기존 계정이 없는 관계로 회원가입 진행. 로그인 재시도");
            kakaoLoginService.socialSignup(post);
        }

//        log.info("Access Token IN Login Controller : {}", accessToken);
//        log.info("Access Token Expires IN Login Controller : {}", kakaoOauth.getATKExpiresIn());
//        log.info("Access Token Expires IN Login Controller : {}", kakaoOauth.getRTKExpiresIn());
//        log.info("UserInfo IN Login Controller: {}", user);
            user.remove("image");
            return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @GetMapping("/auth/kakao/withdrawal")
    public ResponseEntity<?> unlink(@RequestParam String kakaoAccessToken) {
        kakaoLoginService.kakaoUnlink(kakaoAccessToken);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    private String generateRandomId() {
        Random random = new Random();
        random.setSeed(System.currentTimeMillis());
        String s = "";
        for (int i=0;i<4;i++) {
            s += random.nextInt(10);
        }

        return s;
    }
}
