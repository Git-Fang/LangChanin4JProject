package org.fb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.fb.bean.Appointment;

import java.util.List;

/**
* @author 123
* @description 针对表【appointment】的数据库操作Service
* @createDate 2025-11-13 15:57:55
*/
public interface AppointmentService extends IService<Appointment> {


    Appointment getOne(Appointment appointment);

    Appointment getByIdCard(Appointment appointment);

    /**
     * 根据医生姓名查询预约列表
     * @param doctorName 医生姓名
     * @return 预约列表
     */
    List<Appointment> getByDoctorName(String doctorName);

}
