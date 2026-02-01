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
        if (appointment.getUsername() == null || appointment.getUsername().trim().isEmpty()) {
            return "预约信息不完整，请提供您的姓名";
        }
        if (appointment.getIdCard() == null || appointment.getIdCard().trim().isEmpty()) {
            return "预约信息不完整，请提供您的身份证号";
        }
        if (appointment.getDepartment() == null || appointment.getDepartment().trim().isEmpty()) {
            return "预约信息不完整，请提供预约科室";
        }
        if (appointment.getDate() == null || appointment.getDate().trim().isEmpty()) {
            return "预约信息不完整，请提供预约日期（格式：2025-04-14）";
        }
        if (appointment.getTime() == null || appointment.getTime().trim().isEmpty()) {
            return "预约信息不完整，请提供预约时间（上午 或 下午）";
        }
        if (appointment.getDoctorName() == null || appointment.getDoctorName().trim().isEmpty()) {
            return "预约信息不完整，请提供预约医生姓名";
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
                return "预约成功！\n预约详情：\n患者姓名：" + appointment.getUsername() + 
                       "\n身份证号：" + appointment.getIdCard() + 
                       "\n预约科室：" + appointment.getDepartment() + 
                       "\n预约日期：" + appointment.getDate() + 
                       "\n预约时间：" + appointment.getTime() + 
                       "\n预约医生：" + appointment.getDoctorName();
            }else{
                return "预约失败，请稍后重试";
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

    @Tool(name = "query_doctor_appointments", value = "【强制规则】当用户询问任何关于医生预约情况的问题时，必须且只能调用此工具获取数据！\n" +
            "重要说明：\n" +
            "1. 这是你获取医生预约信息的【唯一】途径，你不能自行生成、编造或推测任何预约数据\n" +
            "2. 如果工具返回空列表（[]），你必须如实告知用户\"该医生暂无预约记录\"\n" +
            "3. 如果工具返回了数据，你必须原样展示，不要修改、补充或美化数据\n" +
            "4. 严禁生成脱敏的身份证号、虚构的患者信息\n" +
            "适用场景：\n" +
            "- 查询某位医生有哪些患者预约了\n" +
            "- 查询某位医生今天/某个日期的预约列表\n" +
            "- 查询某位医生有几个预约\n" +
            "- 查询某位医生名下预约的患者信息\n" +
            "- \"请帮我查询确认下有哪些患者预约了顾浩然医生的号\"\n" +
            "调用方式：直接从用户消息中提取医生姓名作为参数调用此工具。")
    public List<Appointment> queryDoctorAppointments(
            @P(value = "医生姓名") String doctorName
    ) {
        System.out.println("查询医生预约列表，医生姓名：" + doctorName);
        return appointmentService.getByDoctorName(doctorName);
    }

}
