package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.DailyPasswordDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PasswordsApiService {

    public List<DailyPasswordDTO> fetchDailyPasswords() {
        // 返回静态数据
        return List.of(
                new DailyPasswordDTO("零号大坝", "0449"),
                new DailyPasswordDTO("长弓溪谷", "0684"),
                new DailyPasswordDTO("巴克什", "0001"),
                new DailyPasswordDTO("航天基地", "0605"),
                new DailyPasswordDTO("潮汐监狱", "0811")
        );
    }
}
