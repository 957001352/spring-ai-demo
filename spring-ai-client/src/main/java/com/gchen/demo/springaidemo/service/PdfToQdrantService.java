package com.gchen.demo.springaidemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfToQdrantService {

    @Autowired
    private QdrantVectorStore qdrantVectorStore;   // Qdrant 向量存储 Bean

    // 读取 PDF 文件文本
    public List<Document> readPdfToDocuments(String pdfFilePath) throws Exception {
        try (PDDocument pdDocument = PDDocument.load(new File(pdfFilePath))) {
            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(pdDocument);
            Document doc = Document.builder()
                    .text(text)
                    .build();

            List<Document> docs = new ArrayList<>();
            docs.add(doc);
            return docs;
        }
    }

    // 使用 TokenTextSplitter 分片
    public List<Document> splitDocuments(List<Document> documents) {
        // 1. 创建分片器，参数含义见之前说明
        int chunkSize = 200;           // 最大200个token
        int minChunkSizeChars = 50;    // 最小50个字符
        int minChunkLengthToEmbed = 10; // 分片最小10个字符才做embedding
        int maxNumChunks = 10000;         // 最多分10片
        boolean keepSeparator = true;  // 保留分隔符

        TokenTextSplitter splitter = new TokenTextSplitter(
                chunkSize,
                minChunkSizeChars,
                minChunkLengthToEmbed,
                maxNumChunks,
                keepSeparator
        );
        return splitter.apply(documents);
    }

    // 将分片文档转embedding并存入Qdrant
    public void saveDocumentsToQdrant(List<Document> documents) {
        // 这里调用 VectorStore 的 upsert
        qdrantVectorStore.add(documents);
    }

    // 一键流程，读PDF -> 分片 -> 存向量库
    public void importPdfToQdrant(String pdfFilePath) throws Exception {
        List<Document> rawDocs = readPdfToDocuments(pdfFilePath);
        List<Document> splitDocs = splitDocuments(rawDocs);
        saveDocumentsToQdrant(splitDocs);
    }
}

