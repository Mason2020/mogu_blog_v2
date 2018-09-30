package com.moxi.mogublog.xo.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.core.query.Criteria;
import org.springframework.data.solr.core.query.HighlightOptions;
import org.springframework.data.solr.core.query.HighlightQuery;
import org.springframework.data.solr.core.query.SimpleHighlightQuery;
import org.springframework.data.solr.core.query.SimpleQuery;
import org.springframework.data.solr.core.query.result.HighlightEntry;
import org.springframework.data.solr.core.query.result.HighlightPage;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moxi.mogublog.utils.StringUtils;
import com.moxi.mogublog.xo.entity.Blog;
import com.moxi.mogublog.xo.entity.BlogSort;
import com.moxi.mogublog.xo.entity.SolrIndex;
import com.moxi.mogublog.xo.entity.Tag;
import com.moxi.mogublog.xo.service.BlogSearchService;
import com.moxi.mogublog.xo.service.BlogService;
import com.moxi.mogublog.xo.service.BlogSortService;
import com.moxi.mogublog.xo.service.TagService;
import com.moxi.mougblog.base.enums.EStatus;
import com.moxi.mougblog.base.global.BaseSysConf;

/**
 * solr索引维护实现
 * @author limboy
 * @create 2018-09-29 16:19
 */
@Service
public class BlogSearchServiceImpl implements BlogSearchService {

    @Autowired
    private SolrTemplate solrTemplate;

    @Autowired
    private BlogService blogService;

    @Autowired
    private TagService tagService;

    @Autowired
    private BlogSortService blogSortService;


    //搜索(带高亮)
    @Override
    public Map<String, Object> search(String keywords) {

        Map<String, Object> map = new HashMap<>();
        map.putAll(searchList(keywords));

        return map;
    }

    //初始化索引
    @Override
    public void initIndex() {
        QueryWrapper<Blog> queryWrapper = new QueryWrapper<Blog>();

        queryWrapper.eq(BaseSysConf.STATUS, EStatus.ENABLE);

        List<Blog> blogList = blogService.list(queryWrapper);
        List<SolrIndex> solrIndexs = new ArrayList<>();

        for(Blog blog : blogList) {
            SolrIndex solrIndex = new SolrIndex();
            solrIndex.setUid(blog.getUid());
            solrIndex.setTitle(blog.getTitle());
            solrIndex.setSummary(blog.getSummary());
            solrIndex.setTag(getTagByBlog(blog));
            solrIndex.setBlogSort(getBlogSortbyBlog(blog));
            solrIndex.setAuthor(blog.getAuthor());
            solrIndex.setUpdateTime(blog.getUpdateTime());
            solrIndexs.add(solrIndex);

//	        SolrInputDocument document = new SolrInputDocument();
//	        document.addField("blog_uid", blog.getUid());
//	        document.addField("blog_title", blog.getTitle());
//	        document.addField("blog_summary", blog.getSummary());
//	        document.addField("blog_tag", getTagByBlog(blog));//获取博客标签
//	        document.addField("blog_sort", getBlogSortbyBlog(blog));//获取博客分类
//	        document.addField("blog_author", blog.getAuthor());
//	        document.addField("blog_updateTime", blog.getUpdateTime());
//	        solrTemplate.saveBean(document);
//	        solrTemplate.commit();
        }
        solrTemplate.saveBeans(solrIndexs);
        solrTemplate.commit();
    }

    //添加索引
    @Override
    public void addIndex(String uid, String title, String summary, String tagUid, String blogSortUid, String author) {

        SolrIndex solrIndex = new SolrIndex();
        solrIndex.setUid(uid);
        solrIndex.setTitle(title);
        solrIndex.setSummary(summary);
        solrIndex.setTag(getTagbyTagUid(tagUid));
        solrIndex.setBlogSort(getBlogSort(blogSortUid));
        solrIndex.setAuthor(author);
        solrIndex.setUpdateTime(new Date());
        solrTemplate.saveBean(solrIndex);
        solrTemplate.commit();

    }

    //更新索引
    @Override
    public void updateIndex(String uid, String title, String summary, String tagUid, String blogSortUid,
                            String author) {

        SolrIndex solrIndex = solrTemplate.getById(uid, SolrIndex.class);
        solrIndex.setUid(uid);
        solrIndex.setTitle(title);
        solrIndex.setSummary(summary);
        solrIndex.setTag(getTagbyTagUid(tagUid));
        solrIndex.setBlogSort(getBlogSort(blogSortUid));
        solrIndex.setAuthor(author);
        solrIndex.setUpdateTime(new Date());
        solrTemplate.saveBean(solrIndex);
        solrTemplate.commit();

    }

    @Override
    public void deleteIndex(String uid) {
        solrTemplate.deleteById(uid);
        solrTemplate.commit();
    }

    @Override
    public void deleteAllIndex() {
        SimpleQuery query=new SimpleQuery("*:*");
        solrTemplate.delete(query);
        solrTemplate.commit();
    }


    private String getBlogSortbyBlog(Blog blog) {
        String blogSortUid = blog.getBlogSortUid();
        String blogSortContent = getBlogSort(blogSortUid);
        return blogSortContent;
    }


    private String getBlogSort(String blogSortUid) {
        BlogSort blogSort = blogSortService.getById(blogSortUid);
        String blogSortContent = blogSort.getContent();
        return blogSortContent;
    }

    private String getTagByBlog(Blog blog) {
        String tagUid = blog.getTagUid();
        String tagContents = getTagbyTagUid(tagUid);
        return tagContents;
    }

    private String getTagbyTagUid(String tagUid) {
        String uids[] = tagUid.split(",");
        List<String> tagContentList = new ArrayList<>();
        for(String uid : uids) {
            Tag tag = tagService.getById(uid);
            if(tag != null && tag.getStatus() != EStatus.DISABLED) {
                tagContentList.add(tag.getContent());
            }
        }
        String tagContents = StringUtils.listTranformString(tagContentList, ",");
        return tagContents;
    }


    private Map<String, Object> searchList(String keywords) {

        Map<String, Object> map = new HashMap<>();

        HighlightQuery query = new SimpleHighlightQuery();
        HighlightOptions highlightOptions = new HighlightOptions().addField("blog_title");
        highlightOptions.setSimplePrefix("<em style = 'color:red')");//高亮前缀
        highlightOptions.setSimplePostfix("</em>");//高亮后缀
        query.setHighlightOptions(highlightOptions);

        //添加查询条件
        Criteria criteria = new Criteria("blog_keywords").is(keywords);
        query.addCriteria(criteria);
        HighlightPage<SolrIndex> page = solrTemplate.queryForHighlightPage(query, SolrIndex.class);

        for (HighlightEntry<SolrIndex> h : page.getHighlighted()) {//循环高亮入口集合
            SolrIndex solrIndex = h.getEntity();//获取原实体类
            if(h.getHighlights().size()>0 && h.getHighlights().get(0).getSnipplets().size()>0) {
                solrIndex.setTitle(h.getHighlights().get(0).getSnipplets().get(0));//设置高亮结果
            }
        }
        map.put("rows", page.getContent());
        return map;
    }

}