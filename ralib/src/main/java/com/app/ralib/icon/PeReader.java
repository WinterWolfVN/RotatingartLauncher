package com.app.ralib.icon;

import android.util.Log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * PE (Portable Executable) 文件读取器
 * 用于解析 Windows EXE/DLL 文件格式
 */
public class PeReader {
    private static final String TAG = "RALib.PeReader";
    
    private final RandomAccessFile file;
    
    public PeReader(RandomAccessFile file) {
        this.file = file;
    }
    
    /**
     * 检查是否为有效的 PE 文件
     */
    public boolean isPeFormat() throws IOException {
        file.seek(0);
        byte[] mzSignature = new byte[2];
        file.read(mzSignature);
        return mzSignature[0] == 'M' && mzSignature[1] == 'Z';
    }
    
    /**
     * 读取 PE 文件头
     */
    public PeHeader readPeHeader() throws IOException {
        file.seek(0);
        
        // 读取 MZ Header
        byte[] mzHeader = new byte[64];
        file.read(mzHeader);
        ByteBuffer mzBuffer = ByteBuffer.wrap(mzHeader).order(ByteOrder.LITTLE_ENDIAN);
        
        // e_lfanew 在偏移 0x3C
        int peOffset = mzBuffer.getInt(0x3C);
        
        // 跳转到 PE Header
        file.seek(peOffset);
        byte[] peSignature = new byte[4];
        file.read(peSignature);
        
        // 验证 PE 签名 "PE\0\0"
        if (peSignature[0] != 'P' || peSignature[1] != 'E' || 
            peSignature[2] != 0 || peSignature[3] != 0) {
            throw new IOException("Invalid PE signature");
        }
        
        // 读取 COFF Header (20 bytes)
        byte[] coffHeader = new byte[20];
        file.read(coffHeader);
        ByteBuffer coffBuffer = ByteBuffer.wrap(coffHeader).order(ByteOrder.LITTLE_ENDIAN);
        
        int machine = coffBuffer.getShort(0) & 0xFFFF;
        int numberOfSections = coffBuffer.getShort(2) & 0xFFFF;
        int sizeOfOptionalHeader = coffBuffer.getShort(16) & 0xFFFF;
        
        // 读取 Optional Header
        long optionalHeaderOffset = file.getFilePointer();
        
        PeHeader header = new PeHeader();
        header.peOffset = peOffset;
        header.numberOfSections = numberOfSections;
        header.optionalHeaderOffset = optionalHeaderOffset;
        header.sizeOfOptionalHeader = sizeOfOptionalHeader;
        
        return header;
    }
    
    /**
     * 读取资源目录表
     */
    public ResourceSection readResourceSection(PeHeader peHeader) throws IOException {
        file.seek(peHeader.optionalHeaderOffset);
        
        // 读取 Optional Header 的前几个字节
        byte[] optHeader = new byte[Math.min(peHeader.sizeOfOptionalHeader, 256)];
        file.read(optHeader);
        ByteBuffer optBuffer = ByteBuffer.wrap(optHeader).order(ByteOrder.LITTLE_ENDIAN);
        
        // 获取 Magic (PE32 = 0x10B, PE32+ = 0x20B)
        int magic = optBuffer.getShort(0) & 0xFFFF;
        boolean is64Bit = (magic == 0x20B);
        
        // DataDirectory 偏移
        int dataDirectoryOffset = is64Bit ? 112 : 96;
        
        // 第3个 DataDirectory 是资源表 (索引2，每个8字节)
        int resourceTableOffset = dataDirectoryOffset + (2 * 8);
        int resourceRva = optBuffer.getInt(resourceTableOffset);
        int resourceSize = optBuffer.getInt(resourceTableOffset + 4);
        
        if (resourceRva == 0 || resourceSize == 0) {
            return null;
        }
        
        // 读取 Section Headers
        long sectionHeaderOffset = peHeader.optionalHeaderOffset + peHeader.sizeOfOptionalHeader;
        file.seek(sectionHeaderOffset);
        
        SectionHeader resourceSectionHeader = null;
        for (int i = 0; i < peHeader.numberOfSections; i++) {
            byte[] sectionData = new byte[40];
            file.read(sectionData);
            ByteBuffer sectionBuffer = ByteBuffer.wrap(sectionData).order(ByteOrder.LITTLE_ENDIAN);
            
            int virtualAddress = sectionBuffer.getInt(12);
            int virtualSize = sectionBuffer.getInt(8);
            int rawDataPointer = sectionBuffer.getInt(20);
            
            // 检查资源 RVA 是否在这个 Section 中
            if (resourceRva >= virtualAddress && resourceRva < virtualAddress + virtualSize) {
                resourceSectionHeader = new SectionHeader();
                resourceSectionHeader.virtualAddress = virtualAddress;
                resourceSectionHeader.rawDataPointer = rawDataPointer;
                break;
            }
        }
        
        if (resourceSectionHeader == null) {
            return null;
        }
        
        // 计算资源表在文件中的实际偏移
        long resourceFileOffset = resourceSectionHeader.rawDataPointer + 
                                  (resourceRva - resourceSectionHeader.virtualAddress);
        
        ResourceSection resourceSection = new ResourceSection();
        resourceSection.sectionHeader = resourceSectionHeader;
        resourceSection.resourceRva = resourceRva;
        resourceSection.resourceFileOffset = resourceFileOffset;
        
        return resourceSection;
    }
    
    /**
     * 读取资源目录
     */
    public ResourceDirectory readResourceDirectory(ResourceSection resourceSection, long offset) throws IOException {
        file.seek(offset);
        
        byte[] dirHeader = new byte[16];
        file.read(dirHeader);
        ByteBuffer buffer = ByteBuffer.wrap(dirHeader).order(ByteOrder.LITTLE_ENDIAN);
        
        ResourceDirectory dir = new ResourceDirectory();
        dir.characteristics = buffer.getInt(0);
        dir.numberOfNamedEntries = buffer.getShort(12) & 0xFFFF;
        dir.numberOfIdEntries = buffer.getShort(14) & 0xFFFF;
        
        int totalEntries = dir.numberOfNamedEntries + dir.numberOfIdEntries;
        dir.entries = new ResourceDirectoryEntry[totalEntries];
        
        // 读取所有条目
        for (int i = 0; i < totalEntries; i++) {
            byte[] entryData = new byte[8];
            file.read(entryData);
            ByteBuffer entryBuffer = ByteBuffer.wrap(entryData).order(ByteOrder.LITTLE_ENDIAN);
            
            ResourceDirectoryEntry entry = new ResourceDirectoryEntry();
            entry.nameOrId = entryBuffer.getInt(0);
            entry.offsetToData = entryBuffer.getInt(4);
            entry.isDirectory = (entry.offsetToData & 0x80000000) != 0;
            entry.offset = entry.offsetToData & 0x7FFFFFFF;
            
            dir.entries[i] = entry;
        }
        
        return dir;
    }
    
    /**
     * 读取资源数据
     */
    public byte[] readResourceData(ResourceSection resourceSection, long dataEntryOffset) throws IOException {
        file.seek(dataEntryOffset);
        
        byte[] dataEntry = new byte[16];
        file.read(dataEntry);
        ByteBuffer buffer = ByteBuffer.wrap(dataEntry).order(ByteOrder.LITTLE_ENDIAN);
        
        int dataRva = buffer.getInt(0);
        int size = buffer.getInt(4);
        
        // RVA 转换为文件偏移
        long fileOffset = resourceSection.sectionHeader.rawDataPointer + 
                         (dataRva - resourceSection.sectionHeader.virtualAddress);
        
        file.seek(fileOffset);
        byte[] data = new byte[size];
        file.read(data);
        
        return data;
    }
    
    /**
     * PE 文件头信息
     */
    public static class PeHeader {
        public int peOffset;
        public int numberOfSections;
        public long optionalHeaderOffset;
        public int sizeOfOptionalHeader;
    }
    
    /**
     * Section Header
     */
    public static class SectionHeader {
        public int virtualAddress;
        public int rawDataPointer;
    }
    
    /**
     * 资源 Section
     */
    public static class ResourceSection {
        public SectionHeader sectionHeader;
        public int resourceRva;
        public long resourceFileOffset;
    }
    
    /**
     * 资源目录
     */
    public static class ResourceDirectory {
        public int characteristics;
        public int numberOfNamedEntries;
        public int numberOfIdEntries;
        public ResourceDirectoryEntry[] entries;
    }
    
    /**
     * 资源目录条目
     */
    public static class ResourceDirectoryEntry {
        public int nameOrId;
        public int offsetToData;
        public boolean isDirectory;
        public int offset;
    }
}

