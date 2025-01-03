package com.study.board.user;

import com.study.board.entity.Board;
import com.study.board.entity.BoardImage;
import com.study.board.repository.BoardImageRepository;
import com.study.board.repository.BoardRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Controller
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final UserImageRepository userImageRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("userCreateForm", new UserCreateForm());
        return "join"; // 회원가입 화면
    }

    @PostMapping("/signup")
    public String signup(@Valid UserCreateForm userCreateForm, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "join";
        }
        if (!userCreateForm.getPassword1().equals(userCreateForm.getPassword2())) {
            bindingResult.rejectValue("password2", "passwordMismatch", "비밀번호가 일치하지 않습니다.");
            return "join";
        }
        try {
            userService.create(userCreateForm);
            model.addAttribute("message", "회원가입이 완료되었습니다. 로그인하세요.");
        } catch (DataIntegrityViolationException e) {
            bindingResult.reject("signupFailed", "이미 사용 중인 사용자명입니다.");
            return "join";
        } catch (Exception e) {
            bindingResult.reject("signupFailed", "회원가입 중 오류가 발생했습니다.");
            return "join";
        }
        return "redirect:/user/login"; // 성공 시 로그인 화면으로 리다이렉트
    }

    @GetMapping("/login")
    public String login() {
        return "login_form"; // 로그인 화면
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpSession session, Model model) {
        SiteUser user = userService.validateUser(username, password);
        if (user != null) {
            session.setAttribute("loggedInUser", user);
            return "redirect:/user/main";
        } else {
            model.addAttribute("error", "아이디 또는 비밀번호가 잘못되었습니다.");
            return "login_form";
        }
    }
    @GetMapping("/profile/{id}")
    public String profile(@PathVariable Long id, Model model) {
        // 사용자 정보 조회
        SiteUser siteUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Board> boards = boardRepository.findByAuthor(siteUser);
        model.addAttribute("user", siteUser);
        model.addAttribute("boards", boards);
        return "profile"; // 로그인 화면
    }

    @GetMapping("/edit")
    public String editUser(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return "redirect:/user/login";
        }
        SiteUser user = userService.getCurrentUser(userDetails.getUsername());
        model.addAttribute("year", user.getBirthdate().getYear());
        model.addAttribute("month", user.getBirthdate().getMonthValue());
        model.addAttribute("day", user.getBirthdate().getDayOfMonth());

        model.addAttribute("user", user);
        model.addAttribute("intro", Optional.ofNullable(user.getIntro()).orElse("자기소개를 입력하세요."));
        model.addAttribute("birthdate", user.getBirthdate());
        model.addAttribute("gender", user.getGender());
        Long id =user.getId();
        List<UserImage> images = userImageRepository.findBySiteUserId(id);
        if (images.isEmpty()) {
            // 기본 이미지 설정
            UserImage defaultImage = new UserImage();
            defaultImage.setUrl("/images/user.png"); // 기본 이미지 경로 설정
            images.add(defaultImage);
        }
        model.addAttribute("images",images);
        return "edit";
    }

    @PostMapping("/edit")
    public String edit(@Valid UserEditForm userEditForm, BindingResult bindingResult, Model model, @AuthenticationPrincipal UserDetails userDetails,@RequestParam(value = "files", required = false) List<MultipartFile> files) {
        if (bindingResult.hasErrors()) {
            return "edit";
        }
        try {
            String username = userDetails.getUsername();
            SiteUser siteUser = userService.getUser(username);
            // 사용자 정보 업데이트
            siteUser.setBirthdate(userEditForm.getBirthdate());
            siteUser.setName(userEditForm.getName());
            siteUser.setIntro(userEditForm.getIntro());
            siteUser.setGender(userEditForm.getGender());
            siteUser.setNickname(userEditForm.getNickname());
            try {
                userService.saveUserImages(siteUser, files);
                System.out.println("유저 이미지저장");
            } catch (Exception e) {
                model.addAttribute("error", "파일 업로드 중 문제가 발생했습니다.");
                System.out.println("유저서비스 이미지저장중 오류"+e.getMessage());
                return "redirect:/user/edit"; // 에러 발생 시 글 작성 화면으로 돌아가기
            }finally{
                userService.updateUser(siteUser);
            }
            model.addAttribute("message", "회원정보가 업데이트되었습니다.");
        } catch (IllegalArgumentException e) {
            // 생년월일 형식 오류 시 처리
            bindingResult.rejectValue("birthdate", "error.birthdate", e.getMessage());
            return "redirect:/user/edit";
        }
        return "redirect:/main";
    }



    //이거슨 탈퇴
    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteAccount(Principal principal) {
        try {
            // 사용자 삭제 로직
            userService.deleteUser(principal);
            return ResponseEntity.ok("계정이 삭제되었습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("계정 삭제에 실패했습니다.");
        }
    }
}
