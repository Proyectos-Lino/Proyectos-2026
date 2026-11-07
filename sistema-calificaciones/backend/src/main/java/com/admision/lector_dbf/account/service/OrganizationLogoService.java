package com.admision.lector_dbf.account.service;

import com.admision.lector_dbf.account.model.OrganizationLogo;
import com.admision.lector_dbf.account.repository.OrganizationProfileRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.util.Locale;

@Service
public class OrganizationLogoService {
    private static final int MAX_BYTES=2*1024*1024;
    private final OrganizationProfileRepository profiles;
    private final OrganizationAccess access;
    public OrganizationLogoService(OrganizationProfileRepository profiles,OrganizationAccess access) {
        this.profiles=profiles; this.access=access;
    }
    public OrganizationLogo get(long id,Authentication auth) {
        access.require(id,auth);
        return profiles.logo(id).orElseThrow(() -> ApiException.notFound("Logo"));
    }
    @Transactional
    public void save(long id,MultipartFile file,Authentication auth) {
        long user=access.require(id,auth,"ADMINISTRADOR").id();
        if (file==null || file.isEmpty() || file.getSize()>MAX_BYTES) throw ApiException.badRequest("El logo debe ser PNG o JPEG y no superar 2 MB");
        try (var input=new MemoryCacheImageInputStream(file.getInputStream())) {
            var readers=ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw ApiException.badRequest("Selecciona un logo PNG o JPEG válido");
            ImageReader reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) throw ApiException.badRequest("El logo debe ser PNG o JPEG");
                reader.setInput(input,true,true);
                if (reader.getWidth(0)>2048 || reader.getHeight(0)>2048) throw ApiException.badRequest("El logo admite hasta 2048 × 2048 píxeles");
                var image=reader.read(0);
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                ImageIO.write(image,"png",output);
                if (output.size()>MAX_BYTES) throw ApiException.badRequest("El logo procesado supera 2 MB; usa una imagen más pequeña");
                profiles.saveLogo(id,output.toByteArray(),user);
            } finally { reader.dispose(); }
        } catch (IOException exception) { throw ApiException.badRequest("No se pudo leer el logo"); }
    }
    @Transactional
    public void delete(long id,Authentication auth) {
        access.require(id,auth,"ADMINISTRADOR"); profiles.deleteLogo(id);
    }
}
