package com.firedemo.demo.Controller;


import com.firedemo.demo.DTO.DailyPasswordDTO;
import com.firedemo.demo.DTO.ProfitItemDTO;
import com.firedemo.demo.Service.PasswordsApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;


@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class GameDataController {
    private final PasswordsApiService passwordsApiService;

    @GetMapping("/daily-passwords")
    public List<DailyPasswordDTO> getDailyPasswords() {
        // 返回静态数据，可从数据库读取，这里先写死
        return passwordsApiService.fetchDailyPasswords();
    }

    @GetMapping("/profits")
    public List<ProfitItemDTO> getProfits() {
        return Arrays.asList(
                new ProfitItemDTO("技术中心", "OLIGHT Baldr Pro R多功能手电", "50,131", "11,140"),
                new ProfitItemDTO("工作台", "7.62*39mm AP SUB", "362,692", "45,336"),
                new ProfitItemDTO("制药台", "精密头盔维修包", "41,541", "5,193"),
                new ProfitItemDTO("防具台", "精英防弹背心", "193,860", "24,232")
        );
    }

}
