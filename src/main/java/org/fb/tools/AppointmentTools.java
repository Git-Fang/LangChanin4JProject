package org.fb.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.fb.bean.Appointment;
import org.fb.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppointmentTools {

    @Autowired
    private AppointmentService appointmentService;

    @Tool(name="book_appointment", value = "预约挂号：根据参数，先执行工具方法queryDepartment查询是否可预约，并直接给用户回答是否可预约，并让用户确认所有预约信息，用户确认后再进行预约。")
    public String bookAppointment(Appointment appointment){
        if (appointment.getDepartment() == null || appointment.getDate() == null ||
            appointment.getTime() == null || appointment.getDoctorName() == null) {
            return "预约信息不完整，请提供完整的预约信息（科室、日期、时间、医生姓名）";
        }

        String originalTime = appointment.getTime();
        if (originalTime != null && originalTime.contains("下午")) {
            appointment.setTime("下午");
        } else if (originalTime != null && originalTime.contains("上午")) {
            appointment.setTime("上午");
        }

        Appointment appointmentDB = appointmentService.getOne(appointment);

        if(appointmentDB == null){
            appointment.setId(null);
            if(appointmentService.save(appointment)){
                return "预约成功，并返回预约详情";
            }else{
                return "预约失败";
            }
        }
        return "您在相同的科室、日期、时间和医生已有预约，无需重复预约";
    }

    @Tool(name="cancel_appointment", value = "取消预约挂号:根据参数，查询预约是否存在，如果存在则删除预约记录并返回取 消预约成功，否则返回取消预约失败")
    public String cancelAppointment(Appointment appointment){
        Appointment appointmentDB = appointmentService.getOne(appointment);

        if(appointmentDB != null){
            //删除预约记录
            if(appointmentService.removeById(appointmentDB.getId())){
                return "取消预约成功";
            }else{
                return "取消预约失败";
            }
        }
        //取消失败
        return "您没有预约记录，请核对预约科室和时间";
    }

    @Tool(name = "query_department", value="查询是否有号源:根据科室名称，日期，时间和医生查询是否有号源，并返回给用户")
    public boolean queryDepartment(
            @P(value = "科室名称") String name,
            @P(value = "日期") String date,
            @P(value = "时间，可选值：上午、下午") String time,
            @P(value = "医生名称", required = false) String doctorName
    ) {
        System.out.println("查询是否有号源");
        System.out.println("科室名称：" + name);
        System.out.println("日期：" + date);
        System.out.println("时间：" + time);
        System.out.println("医生名称：" + doctorName);
//TODO 维护医生的排班信息：
//如果没有指定医生名字，则根据其他条件查询是否有可以预约的医生（有返回true，否则返回false）；
//如果指定了医生名字，则判断医生是否有排班（没有排版返回false）
//如果有排班，则判断医生排班时间段是否已约满（约满返回false，有空闲时间返回true）
        return true;
    }

    @Tool(name = "query_info", value="查询患者是否已经挂号:根据患者姓名和身份证号查询该患者是否已经挂号，并返回给用户")
    public Appointment queryInfo(
            @P(value = "姓名") String username,
            @P(value = "身份证号") String idCard
    ) {
        System.out.println("患者姓名："+username+";身份证号：" + idCard);

        Appointment appointment = new Appointment();
        appointment.setUsername(username);
        appointment.setIdCard(idCard);
        Appointment appointmentDB = appointmentService.getByIdCard(appointment);
        return appointmentDB;

    }

    @Tool(name = "query_doctor_appointments", value = "【重要】当用户询问任何关于医生预约情况的问题时，必须调用此工具！\n" +
            "适用场景包括但不限于：\n" +
            "- 查询某位医生有哪些患者预约了\n" +
            "- 查询某位医生今天/某个日期的预约列表\n" +
            "- 查询某位医生有几个预约\n" +
            "- 查询某位医生名下预约的患者信息\n" +
            "用户可能会这样问：\n" +
            "- \"查询张医生今天有哪些患者预约\"\n" +
            "- \"顾浩然医生有几个预约\"\n" +
            "- \"李医生今天的预约情况\"\n" +
            "- \"看看王医生名下有哪些预约\"\n" +
            "请直接提取用户消息中的医生姓名，然后调用此工具查询所有相关预约记录。")
    public List<Appointment> queryDoctorAppointments(
            @P(value = "医生姓名") String doctorName
    ) {
        System.out.println("查询医生预约列表，医生姓名：" + doctorName);
        return appointmentService.getByDoctorName(doctorName);
    }

}
