package com.vonhof.babelshark.converter;

import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.BeanMapper;
import com.vonhof.babelshark.MappedBean;
import com.vonhof.babelshark.MappedBean.ObjectField;
import com.vonhof.babelshark.SharkConverter;
import com.vonhof.babelshark.annotation.TypeResolver;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.babelshark.impl.DefaultBeanMapper;
import com.vonhof.babelshark.node.ObjectNode;
import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.babelshark.node.SharkType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class BeanConverter<T> implements SharkConverter<T> {
    
    private final BeanMapper beanMapper;

    public BeanConverter() {
        this(new DefaultBeanMapper());
    }
    
    public BeanConverter(BeanMapper beanMapper) {
        this.beanMapper = beanMapper;
    }

    public <U> T deserialize(BabelSharkInstance bs, SharkNode node, SharkType<T, U> type) throws MappingException {
        if (!node.is(SharkNode.NodeType.MAP)) {
            throw new MappingException(String.format("Could not convert %s to %s",node,type));
        }
        ObjectNode objNode = (ObjectNode) node;
        MappedBean<?> map = beanMapper.getMap(type.getType());
        Object out = null;
        
        //Check for type resolver
        Class<?> clz = type.getType();
        TypeResolver typeResolver = clz.getAnnotation(TypeResolver.class);
        if (typeResolver != null) {
            try {
                ObjectField typeField = map.getField(typeResolver.field());
                Object typeValue = bs.read(objNode.get(typeResolver.field()),typeField.getType());
                Method resolverMethod = clz.getMethod(typeResolver.resolverMethod(),typeField.getType().getType());
                
                if (!Modifier.isStatic(resolverMethod.getModifiers())) {
                    throw new Exception("Type resolver method has to be static");
                }
                Class<?> resolvedClz = (Class) resolverMethod.invoke(null, typeValue);
                map = beanMapper.getMap(resolvedClz);
                out = resolvedClz.newInstance();
            } catch (Throwable ex) {
                throw new MappingException(ex);
            }
        }
        
        //If no type resolver found - create default instance
        if (out == null)
            out = map.newInstance(objNode);
        for(String field:objNode.getFields()) {
            final MappedBean.ObjectField oField = map.getField(field);
            if (oField == null || !oField.hasSetter()) continue;
            try {
                Object value = bs.read(objNode.get(field), oField.getType());
                oField.set(out, value);
            } catch (Exception ex) {
                throw new BabelSharkDeserializeException(String.format("Failed to read value for field: %s", field), ex);
            }
        }
        return (T) out;
    }

    public SharkNode serialize(BabelSharkInstance bs, Object instance) throws MappingException {
        ObjectNode out = new ObjectNode();
        SharkType type = SharkType.get(instance.getClass());
        MappedBean<Object> map = beanMapper.getMap(type.getType());
        for (String field:map.getFieldList()) {
            MappedBean.ObjectField oField = map.getField(field);
            if (!oField.hasGetter()) continue;
            Object value = oField.get(instance);
            try {
                out.put(field,bs.write(value));
            } catch (Exception ex) {
                throw new BabelSharkSerializeException(String.format("Failed to write value for field: %s => %s", field, value), ex);
            }
        }
        return out;
    }

}
