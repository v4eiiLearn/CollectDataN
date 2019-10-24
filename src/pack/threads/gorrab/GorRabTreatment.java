package pack.threads.gorrab;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import pack.db.entity.Category;
import pack.services.ThreadService;
import pack.util.Vacancy;
import pack.view.controllers.CollectViewController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Exchanger;

/**
 * Обрабатывающие потоки
 * @author v4e
 */
public class GorRabTreatment extends Thread {

    private Exchanger<Vacancy> exchanger;
    private Exchanger<HashMap<String, Integer>> exchangerToAnalysis;
    private Exchanger<ArrayList<String>> exchangerToLog;
    
    private Vacancy vacancy;
    private volatile String name;
    // Статус потока
    private boolean active;
    // Попадания ключевых слов
    private HashMap<String, Integer> hits;
    private Document doc;
    // Для генерации лога
    private ArrayList<String> logData;
    private Category category;
    private GorRabCategoryParser gorRabCategoryParser;

    /**
     * Инициализация объектов
     * @param exchanger объект обмена с распределяющим потоком
     * @param exchangerToAnalysis объект для обмена с финальным потоком
     * @param exchangerToLog объект обмена для генерации лога
     * @param group группа принадлежности распределяющего потока
     * @param name имя потока
     * @param category категория обработки
     * @param gorRabCategoryParser парсер категории
     */
    GorRabTreatment(Exchanger<Vacancy> exchanger, Exchanger<HashMap<String, Integer>> exchangerToAnalysis,
                    Exchanger<ArrayList<String>> exchangerToLog, ThreadGroup group, String name, Category category,
                    GorRabCategoryParser gorRabCategoryParser)
    {
        super(group, name);
        this.gorRabCategoryParser = gorRabCategoryParser;
        this.exchanger = exchanger;
        this.name = name;
        this.category = category;
        active = true;
        hits = new HashMap<>();
        this.exchangerToAnalysis = exchangerToAnalysis;
        if (CollectViewController.getLogGenerate()) {
            logData = new ArrayList<>();
            this.exchangerToLog = exchangerToLog;
        }
    }

    @Override
    public void run()
    {
        while(isActive())
        {
            try
            {
                Vacancy v = null;
                vacancy = exchanger.exchange(v);
                if (vacancy != null)
                {
                    doc = Jsoup.connect(vacancy.getUrlVacancy()).get();
                    String textVacancy = doc.getElementsByClass("vacancy-description clearfix").text();
                    ThreadService.firstAnalysis(textVacancy, hits, gorRabCategoryParser.getSkillsForCategory());
                    System.out.println(this.getName() + " " + vacancy.getUrlVacancy());
                    if (CollectViewController.getLogGenerate())
                        logData.add(name + " " + vacancy.getNameVacancy() + "\n");
                    Thread.sleep(300);
                }
            }
            catch (InterruptedException | IOException ex)
            {
                if (!isActive())
                    break;
            }
        }

        gorRabCategoryParser.getProcessesCompletion().put(name, true);
        HashMap<String, Integer> tmp = null;
        ArrayList<String> tmpList = null;
        boolean stop = false;
        boolean mapSend = false, listSend = false;
        do {
            try {
                if (!mapSend)
                    tmp = exchangerToAnalysis.exchange(hits);
                if (!listSend && CollectViewController.getLogGenerate())
                    tmpList = exchangerToLog.exchange(logData);
    
                if (tmp == null && !mapSend) {
                    mapSend = true;
                    System.out.println(name + " отправил карту");
                }
                if (tmpList == null && !listSend && CollectViewController.getLogGenerate()) {
                    listSend = true;
                    System.out.println(name + " отправил list");
                }
    
                if (tmp == null && tmpList == null)
                    stop = true;
            }
            catch (InterruptedException ex) {
                // Возможно попадание после срабатывания interrupt от распределяющего потока для сбрасывания "зависших" потоков из ожидания
                System.out.println(name + " псевдо остановка, все ОК");
            }
        }
        while (!stop);
        
        //MainViewController.getCollectViewController().getTextInfo().appendText(name + " stopped \n");
    }

    private boolean isActive()
    {
        return active;
    }

    void setActive(boolean active)
    {
        this.active = active;
    }

}
