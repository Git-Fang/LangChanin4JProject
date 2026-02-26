import org.fb.service.KnowledgeBaseRetrievalService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class test_knowledge_retrieval {
    public static void main(String[] args) {
        // 加载Spring上下文
        ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
        
        // 获取KnowledgeBaseRetrievalService实例
        KnowledgeBaseRetrievalService service = context.getBean(KnowledgeBaseRetrievalService.class);
        
        // 测试方彪简历检索
        System.out.println("=== 测试方彪简历检索 ===");
        String result = service.searchPersonalProjectExperience("方彪");
        System.out.println(result);
        
        // 测试完整问题的检索
        System.out.println("\n=== 测试完整问题检索 ===");
        String fullQuestion = "结合知识库中的方彪简历回答，方彪有过几份工作经历？";
        result = service.searchKnowledgeBase(fullQuestion);
        System.out.println(result);
        
        // 关闭上下文
        ((ClassPathXmlApplicationContext) context).close();
    }
}
